# vpnbot

A Telegram bot that starts Tailscale exit nodes in AWS as ECS Fargate tasks. A node stops
itself after a period with no connected devices (Python node agent in `docker/node_agent/src`).
It is a small home project used by a handful of people: prefer simple, readable solutions.
Planned work lives in `docs/roadmap.md`.

## Build & test

- Java 21, Maven: `mvn -B verify` (compiles, runs unit tests, builds the shaded Lambda jar).
- Node agent (Python 3.11, deps pinned in `docker/requirements.txt`):
  `cd docker/node_agent && PYTHONPATH=src python3 -m unittest discover -s tests -t .`
  (production code in `docker/node_agent/src/node_agent`, tests in `docker/node_agent/tests`)
- The build rewrites `dependency-reduced-pom.xml`; never commit that change
  (`git checkout dependency-reduced-pom.xml`).
- Shell script tests (need `jq`): `.github/scripts/region-item-test.sh`, `.github/scripts/publish-live-version-test.sh`,
  `scripts/migrate-pending-users-test.sh`.
- `java-tests.yml` runs the build and the shell script tests on pull requests.
- Deployment: a push to `main` runs `ci-cd.yml` (jar + alias); a push to `release/**` also runs the Lambda
  stack deploy (`deploy-tgbot-lambda.yml`, called as a reusable workflow); region (ECS) deploys are manual
  (`.github/workflows/deploy-*.yml`). API Gateway invokes the
  Lambda alias `live` (SnapStart): every code/config deploy ends with `publish-live-version.sh`, which
  publishes a version and moves the alias. `BotDependencies.build()` runs at publish time, not on first request.
  The jar lives in the S3 bucket named by the repository variable `LAMBDA_JAR_BUCKET`.
- Versioning is calendar-based (`YYYY.MM[.N]`, e.g. `2026.10-SNAPSHOT` while in progress; after a release
  `main` continues as the patch snapshot, `2026.10.1-SNAPSHOT`); `pom.xml` is the only place holding the
  version (the workflows read it). `2.0` (branch `release/2.0`, tag `v2.0`)
  is the last version before the button UI.

## Architecture

```
TailscaleVpnLambdaHandler  (API Gateway → Lambda entry point; the request flow only)
 └─ BotDependencies         composition root: builds the whole object graph from Externals (AWS clients,
                            Telegram sender factory, Clock) + Settings (env + application.yml), primes it
 ├─ UiRouter (ui)           button taps (callback queries: sealed UiAction records), /start, /menu, plain text
 │   ├─ HomeController, LaunchController, NodeController (stop/use) + NodeAccess (ui.controller)
 │   ├─ AccessController (request access → admins' role checkboxes), UsersController (users screen)
 │   ├─ RegionController (Regions screen: enabled regions without infra → root users get
 │   │   [Deployed, notify]/[Decline]; "ready" = the REGION record written last by the deploy workflow)
 │   ├─ *Screen classes (ui.screen): pure functions, data → Screen (template + params + buttons)
 │   └─ UiMessenger (ui.messenger): sends/edits Telegram messages, translates, HTML-escapes
 └─ SystemCommandDispatcher system commands /help, /version, /supportedRegions, /describeRoles
                            (dispatcher.command.*); replies are Screens with a Menu button; any other
                            "/command" gets the "not supported" reply with the /help list
Services: TailscaleNodeService (tailscale) → EcsManager (ecs); Authorizer + PermissionsService (auth);
          RegionCatalog (ec2: DescribeRegions in the Lambda's region, cached); RegionRequestService (region)
Identity: TgUser(id, username) – permissions and node ownership (RunBy tag) are keyed by the Telegram
          user id; new users come in through the Request access button. PENDING_USER records (users
          pre-registered by username before 2026.10) are still activated at first request by
          PendingUserActivator but never created: that code is @Deprecated(forRemoval) and goes once no
          PENDING_USER record is left (docs/specs/cleanup-2026-10.md §4.3). Authorizer.hasPermission is
          read-only; roles (UserRole) are bundles of permissions, root is a permission (ROOT_ACCESS)
Config:   Settings.fromEnvironment() reads the env and application.yml once (YamlApplicationConfiguration
          sections); nothing else reads System.getenv. DynamoDB table `vpnbot`
          holds runtime config: pk REGION/USER/PENDING_USER/ACCESS_REQUEST/REGION_REQUEST/TG_UPDATE_LOCK, beans in
          `dynamodb` (Enhanced Client),
          optional in-memory cache (CONFIG_CACHE_ENABLED, CONFIG_CACHE_TTL_SEC); workflows write regions;
          bot token from Secrets Manager (BOT_TOKEN_SECRET_ID)
Dedup:    a Telegram re-delivery is dropped by a conditional put of a TG_UPDATE_LOCK record (24 h TTL)
i18n:     Translator replaces ${key} with values from messages[_<lang>].properties, then String.formatted(params)
Metrics:  RequestMetrics → one CloudWatch EMF line per request on stdout (METRICS_ENABLED flag)

Node lifecycle: LaunchController starts the ECS task with TG_* env vars rendered by NodeNotifications
(texts already translated, with {{HOSTNAME}}/{{PUBLIC_IP}} placeholders) and returns. The node agent
(docker/node_agent) brings Tailscale up, edits the progress message into the node card, sends a silent
warning before the idle stop (deleted on stop or when a device connects) and, on any stop, edits the
same message into the stopped card – all via the Telegram Bot API.
```

## Conventions

- Constructor injection with `final` fields and Lombok `@RequiredArgsConstructor`; logging with `@Log4j2`.
- Wiring lives in `BotDependencies` (the composition root), not in business code: constructor injection only,
  externals and config passed in, one implementation per interface chosen there, no work in constructors
  beyond assignment, no dependency cycles – so that a DI framework (Dagger) could replace it mechanically
  (docs/specs/cleanup-2026-10.md §4.7).
- Interface + implementation for anything that talks to the outside world (Telegram, AWS).
- Records for small immutable values. No static mutable state.
  Exception: DynamoDB beans (`org.github.akarkin1.dynamodb`) are mutable JavaBeans as the Enhanced
  Client requires; they stay inside the DynamoDB services, which map them to domain types.
- User-facing text lives in `src/main/resources/messages.properties` and `messages_ru.properties`.
  Both files are ASCII: non-ASCII characters are written as `\uXXXX` escapes. Never put emoji in
  properties files – emoji belong in Java code. UI property values contain no `%` format specifiers.
- Messages sent with HTML parse mode must HTML-escape every dynamic value.
- Do not add dependencies, change existing text commands or refactor unrelated code without asking.

## Tests

- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`), see `SystemCommandDispatcherTest`.
- `BotDependenciesTest` builds the whole graph from mocked externals: a wiring regression shows up there.
- Test class per production class, in the same package under `src/test/java`.
- `src/test/resources` has its own `messages*.properties` and `application.yml` that shadow the
  main ones on the test classpath; tests that need the real files read them from `src/main/resources`.
- Classes that receive `RequestMetrics` run their work inside `time(...)`: a plain Mockito mock
  would skip it – use a pass-through (`RecordingRequestMetrics`) in tests.
- DynamoDB services are tested against `FakeTable` (a `DynamoDbTable` mock that records calls and
  whether they ran inside `metrics.time(...)`); bean schemas are tested with the real `TableSchema`.
- Node agent: stdlib `unittest` + `unittest.mock`, fakes in `docker/node_agent/tests/fakes.py`;
  everything external (subprocess, sleep, clock, HTTP session, boto3) is injected.

## Workflow

- Branches: `feature/<short-meaningful-name>`; small focused commits with imperative messages.
- Feature specs live in `docs/specs/`; implement them as written and record deviations in the
  spec's decision log.
