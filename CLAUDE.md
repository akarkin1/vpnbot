# vpnbot

A Telegram bot that starts Tailscale exit nodes in AWS as ECS Fargate tasks. A node stops
itself after a period with no connected devices (Python node agent in `docker/node_agent/src`).
It is a small home project used by a handful of people: prefer simple, readable solutions.
Planned work lives in `docs/roadmap.md`.

## Build & test

- Java 21, Maven: `mvn -B verify` (compiles, runs unit tests, builds the shaded Lambda jar).
- Node agent (Python 3.9, deps pinned in `docker/requirements.txt`):
  `cd docker/node_agent && PYTHONPATH=src python3 -m unittest discover -s tests -t .`
  (production code in `docker/node_agent/src/node_agent`, tests in `docker/node_agent/tests`)
- The build rewrites `dependency-reduced-pom.xml`; never commit that change
  (`git checkout dependency-reduced-pom.xml`).
- Shell script tests (need `jq`): `.github/scripts/region-item-test.sh`, `.github/scripts/publish-live-version-test.sh`.
- Deployment is manual via GitHub Actions (`.github/workflows/deploy-*.yml`). API Gateway invokes the
  Lambda alias `live` (SnapStart): every code/config deploy ends with `publish-live-version.sh`, which
  publishes a version and moves the alias. The static block runs at publish time, not on first request.

## Architecture

```
TailscaleVpnLambdaHandler  (API Gateway → Lambda entry point, wiring in a static block)
 ├─ UiRouter (ui)           button taps (callback queries), /start, /menu, plain text
 │   ├─ HomeController, LaunchController, NodeController (stop/use) + NodeAccess (ui.controller)
 │   ├─ *Screen classes (ui.screen): pure functions, data → Screen (template + params + buttons)
 │   └─ UiMessenger (ui.messenger): sends/edits Telegram messages, translates, HTML-escapes
 └─ CommandDispatcher       every other "/command" (dispatcher.command.*), plain-text replies
Services: TailscaleNodeService (tailscale) → EcsManager (ecs); Authorizer + PermissionsService (auth)
Config:   application.yml → YamlApplicationConfiguration (via ConfigManager); DynamoDB table `vpnbot`
          holds runtime config: pk REGION/USER/TG_UPDATE_LOCK, beans in `dynamodb` (Enhanced Client),
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
- Wiring lives in `*Configurer` classes (see `TailscaleEcsNodeServiceConfigurer`), not in business code.
- Interface + implementation for anything that talks to the outside world (Telegram, AWS).
- Records for small immutable values. No new static mutable state (`TgRequestContext` is legacy).
  Exception: DynamoDB beans (`org.github.akarkin1.dynamodb`) are mutable JavaBeans as the Enhanced
  Client requires; they stay inside the DynamoDB services, which map them to domain types.
- User-facing text lives in `src/main/resources/messages.properties` and `messages_ru.properties`.
  Both files are ASCII: non-ASCII characters are written as `\uXXXX` escapes. Never put emoji in
  properties files – emoji belong in Java code. UI property values contain no `%` format specifiers.
- Messages sent with HTML parse mode must HTML-escape every dynamic value.
- Do not add dependencies, change existing text commands or refactor unrelated code without asking.

## Tests

- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`), see `HelpCommandV2Test`.
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
