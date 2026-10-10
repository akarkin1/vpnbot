# Spec: Phase 3 – Config in DynamoDB, no Lambda VPC, SnapStart

Status: draft for review · Branch: `feature/infra-cleanup` (based on `main`) · Roadmap: `docs/roadmap.md`

## 1. Goal

1. Runtime data moves from S3 / EFS to **one DynamoDB table** (`vpnbot`): supported regions with
   their stack outputs, user permissions, and the Telegram update lock (deduplication).
2. The Lambda leaves its VPC: VPC, subnets, internet gateway, route tables, NAT instance + ENI + EIP,
   security groups and EFS are deleted (≈ $13.2/month, see §11). The plain `BOT_TOKEN` env var goes.
3. **SnapStart** for the Lambda: cold start ≈ 3.7 s init + ≈ 0.75 s first-request work today
   (prod validation 2026-10-09) should drop to a sub-second restore.

## 2. Non-goals

- No UI, text-command, node-agent (Python) or node-stack changes, except the node log retention (§7.2).
- No new features (Phase 4/5), no change to permissions semantics or roles.
- No new dependencies beyond swapping the AWS SDK module `s3` for `dynamodb-enhanced` (approved).
- No refactoring beyond what this spec lists.

## 3. Ground rules

Same as `docs/specs/node-lifecycle-2a.md` §3 (CLAUDE.md, existing patterns, exact contracts,
file ownership, `SPEC DEVIATION: <what> / <why> / <proposed option>` escalation).

## 4. Delivery: three deploys from the same branch

Each deploy is a milestone on `feature/infra-cleanup`: implemented, tested, pushed, deployed and
verified by the owner before the next one starts.

| Deploy | Content | Rollback |
|---|---|---|
| **A – DynamoDB** | table, Lambda reads/writes DynamoDB, workflows write only the table (no S3 config any more), one-off migration | run "VPN Bot Lambda CI-CD" from the last commit before A; if config changed since A, bring the S3 files up to date by hand (a local sync script can be written then – not part of this phase) |
| **B – No VPC** | Lambda out of the VPC; delete network + EFS resources; remove `BOT_TOKEN`; timeouts/retention | revert the template commit and redeploy the stack (resources are recreated empty – the DynamoDB data stays) |
| **C – SnapStart** | published versions + alias `live`, API Gateway → alias, workflows publish versions | revert the template commit and redeploy |

## 5. Data model

One table, on-demand billing (`PAY_PER_REQUEST`), TTL attribute `expiresAt`,
`DeletionPolicy: Retain` + `UpdateReplacePolicy: Retain`, in the Lambda stack's region
(`eu-central-1`, which must equal the GitHub variable `CONFIG_BASE_REGION`).

Key: `pk` (S, partition key) = record type, `sk` (S, sort key) = id.

| pk | sk | Attributes |
|---|---|---|
| `REGION` | region id, e.g. `eu-central-1` | `ecsClusterName` (S), `ecsTaskDefinitionArn` (S), `subnetId` (S), `securityGroupId` (S), `updatedAt` (S, ISO-8601 UTC) |
| `USER` | Telegram username (as stored today, no case change) | `permissions` (SS, `Permission` enum names, never empty) |
| `TG_UPDATE_LOCK` | Telegram `update_id` as a decimal string | `expiresAt` (N, epoch seconds = now + 24 h), `receivedAt` (N, epoch ms) |

Rules:
- A user without permissions has no record (`SS` cannot be empty; matches today's "empty = delete").
- A region exists in the table exactly when its stack is deployed (written by the deploy workflow,
  removed by the delete workflow), so the region list and its parameters cannot drift apart.
- 24 h lock lifetime: Telegram keeps undelivered updates for at most 24 h. TTL deletion is free and
  may lag; an expired-but-not-yet-deleted lock still counts as a duplicate (harmless).

Access patterns (all eventually consistent reads):

| Who | Operation |
|---|---|
| Lambda – regions | `Query pk = REGION` (all regions); `GetItem (REGION, <region>)` (one region's parameters) |
| Lambda – permissions | `Query pk = USER` (all users, paginated); `PutItem` (set permissions); `DeleteItem` (no permissions) |
| Lambda – deduplication | `PutItem (TG_UPDATE_LOCK, <id>)` with `ConditionExpression attribute_not_exists(pk)` |
| ECS deploy workflow | `put-item (REGION, <region>)` built from the stack outputs |
| ECS delete workflow | `delete-item (REGION, <region>)` |
| Lambda deploy workflow | `put-item (USER, <root>)` with `attribute_not_exists(pk)` (bootstrap, never overwrites) |

In-memory config cache behind a feature flag, to be measured (`DynamoDbMs`) and retired if it
doesn't pay off:
- `CONFIG_CACHE_ENABLED` (default `false`): when `true`, the region list, per-region parameters and
  user permissions are cached per Lambda instance for `CONFIG_CACHE_TTL_SEC`; when `false`, every
  request reads DynamoDB (≈ 5–10 ms per call; changes take effect immediately).
- `CONFIG_CACHE_TTL_SEC` (default `300`, unchanged) is that TTL and, as today, the webhook-secret
  cache TTL (the webhook secret is cached regardless of the flag).
- Caches fill on first use (never during init), so SnapStart snapshots contain no config.

## 6. Deploy A – DynamoDB

### 6.1 CloudFormation (`cloudformation/vpn-configurer-lambda.yml`)

- New resource `ConfigTable` (`AWS::DynamoDB::Table`): `TableName: vpnbot`, attributes `pk` S /
  `sk` S, `KeySchema` HASH `pk` / RANGE `sk`, `BillingMode: PAY_PER_REQUEST`,
  `TimeToLiveSpecification { AttributeName: expiresAt, Enabled: true }`, `DeletionPolicy: Retain`,
  `UpdateReplacePolicy: Retain`.
- `LambdaRole`: allow `dynamodb:GetItem`, `dynamodb:PutItem`, `dynamodb:DeleteItem`, `dynamodb:Query`
  on `!GetAtt ConfigTable.Arn`. The S3, EFS and VPC statements stay until Deploy B (the old code keeps
  working between the stack deploy and the code deploy of §6.4).
- Lambda env var `CONFIG_TABLE_NAME: !Ref ConfigTable`.
- New parameter `EnvConfigCacheEnabled` (String, default `"false"`, description "Whether the Lambda caches
  regions and permissions read from DynamoDB for EnvConfigCacheTtlSec") → env `CONFIG_CACHE_ENABLED`.
- Update the `EnvConfigCacheTtlSec` description: "TTL in seconds of the config cache (when enabled)
  and of the webhook secret read from Secrets Manager".
- The Lambda stays in the VPC; it reaches DynamoDB through the NAT instance.

### 6.2 Java

DynamoDB access uses the **Enhanced Client** (`software.amazon.awssdk:dynamodb-enhanced`): one bean
class per record type, each a typed view (`DynamoDbTable<T>`) of the same physical table. Attribute
names are the bean property names (camelCase); only the keys are renamed to `pk`/`sk`.

New package `org.github.akarkin1.dynamodb` – persistence beans (mutable JavaBeans as the Enhanced Client
requires; an agreed exception to "records for small values"). They never leave the DynamoDB services,
which map them to the existing domain types.

```java
@DynamoDbBean @Data @NoArgsConstructor
public class RegionRecord {
  public static final String TYPE = "REGION";
  private String type = TYPE;                 // stored as "pk"
  private String regionId;                    // stored as "sk"
  private String ecsClusterName;
  private String ecsTaskDefinitionArn;
  private String subnetId;
  private String securityGroupId;
  private String updatedAt;

  @DynamoDbPartitionKey @DynamoDbAttribute("pk") public String getType() { return type; }
  @DynamoDbSortKey @DynamoDbAttribute("sk") public String getRegionId() { return regionId; }
}

@DynamoDbBean @Data @NoArgsConstructor
public class UserRecord {
  public static final String TYPE = "USER";
  private String type = TYPE;                 // "pk"
  private String username;                    // "sk"
  private Set<String> permissions;            // String Set of Permission names, never empty
  // key getters annotated as in RegionRecord
}

@DynamoDbBean @Data @NoArgsConstructor
public class TgUpdateLock {
  public static final String TYPE = "TG_UPDATE_LOCK";
  private String type = TYPE;                 // "pk"
  private String updateId;                    // "sk", Telegram update_id as a decimal string
  private Long expiresAt;                      // epoch seconds, TTL attribute
  private Long receivedAt;                    // epoch millis
  // key getters annotated as in RegionRecord
}

/** The typed views of the config table; built once at init (schema creation uses reflection). */
public record ConfigTables(DynamoDbTable<RegionRecord> regions,
                           DynamoDbTable<UserRecord> users,
                           DynamoDbTable<TgUpdateLock> updateLocks) {
  public static ConfigTables create(DynamoDbEnhancedClient client, String tableName);  // TableSchema.fromBean(...)
}
```

Regions – `org.github.akarkin1.config.DynamoDbTaskConfigService implements TaskConfigService`
(`DynamoDbTable<RegionRecord> regions, RequestMetrics metrics`):
- `getSupportedRegions()`: query `pk = REGION` (all pages); region ids that are not known to the AWS
  SDK are skipped with a warning (same `KNOWN_REGIONS` logic as `S3TaskConfigService` today, moved here).
- `getTaskRuntimeParameters(Region)`: `getItem (REGION, region.id())` → `TaskRuntimeParameters`
  (`ecsClusterName`, `ecsTaskDefinition` ← `ecsTaskDefinitionArn`, `subnetId`, `securityGroupId`).
  Missing item or attribute → `IllegalStateException("Region <id> is not configured")`.
- Every DynamoDB call runs inside `metrics.time(MetricComponent.DYNAMODB, …)`; query results are
  collected into a list inside `time(...)` (the Enhanced Client's iterables fetch pages lazily).

Permissions – move `PermissionsService` and `PermissionsServiceConfigurer` from `auth.s3` to `auth`
(the `auth.s3` package is deleted). New `org.github.akarkin1.auth.DynamoDbPermissionsService
implements PermissionsService` (`DynamoDbTable<UserRecord> users, RequestMetrics metrics`):
- `getUserPermissions()`: query `pk = USER` (all pages) → `Map<username, List<Permission>>`; unknown
  permission names are skipped with a warning.
- `updateUserPermissions(username, permissions)`: null/empty → `deleteItem (USER, username)`;
  otherwise `putItem` of a `UserRecord` with the set's enum names (replaces the record).
- Calls timed with `MetricComponent.DYNAMODB`.

Deduplication – `UpdateEventsRegistry` becomes one atomic method:

```java
public interface UpdateEventsRegistry {
  /** Records the update; false if it was already recorded (a re-delivery). */
  boolean register(Update update);
}
```

`org.github.akarkin1.deduplication.DynamoDbUpdateEventsRegistry implements UpdateEventsRegistry`
(`DynamoDbTable<TgUpdateLock> updateLocks, Clock clock, RequestMetrics metrics`):
- `putItem(PutItemEnhancedRequest)` of a `TgUpdateLock` (`updateId` = `String.valueOf(update_id)`,
  `expiresAt` = now + 24 h in epoch s, `receivedAt` = now in epoch ms) with
  `conditionExpression("attribute_not_exists(pk)")`.
- `ConditionalCheckFailedException` → `false`. Any other exception → logged as error, `true`
  (fail open, as the file registry does today: a failing lock must not block the bot).
- `TailscaleVpnLambdaHandler.handleUpdate`: `if (!EVENTS_REGISTRY.register(update)) { log "Skipping
  duplicated event"; return; }` replaces the `hasAlreadyProcessed` + `registerEvent` pair.

Wiring:
- The handler's static block creates `ConfigTables` once (`DynamoDbClient.create()` – region from the
  Lambda's `AWS_REGION` – → `DynamoDbEnhancedClient` → `ConfigTables.create(…, getConfigTableName())`)
  and passes the views to `TailscaleEcsNodeServiceConfigurer.configure(…)`,
  `PermissionsServiceConfigurer.configure(…)` and the registry.
- `ConfigManager`: add `getConfigTableName()` (env `CONFIG_TABLE_NAME`, default `vpnbot`) and
  `isConfigCacheEnabled()` (env `CONFIG_CACHE_ENABLED`, `true`/`false`, default `false`, parsed like
  `isMetricsEnabled()`); remove `getEventRootDir()`, `getEventTtlSec()` and their constants.
- Caches (decorators, wired by the configurers only when `isConfigCacheEnabled()`):
  - `config.CachedS3TaskConfigService` → renamed `config.CachedTaskConfigService`, logic unchanged
    (TTL per value, `Clock`, zero TTL = no caching).
  - `auth.s3.CachingPermissionsService` → moved to `auth.CachingPermissionsService` and given the same
    TTL behaviour (`PermissionsService delegate, Duration ttl, Clock clock`; today it never expires);
    `updateUserPermissions` still invalidates before delegating.

Metrics: `MetricComponent.S3` → `DYNAMODB`, EMF metric name `S3Ms` → `DynamoDbMs`.

Delete: `S3TaskConfigService`, `auth.s3.S3PermissionsService`, `s3.S3ConfigManager`, `config.exception.S3DownloadFailureException`,
`deduplication.FSUpdateEventsRegistry`, `YamlApplicationConfiguration.S3Configuration` + the `s3:`
section of `application.yml` (main and test), and their tests. `pom.xml`: `s3` → `dynamodb-enhanced`
(which brings `dynamodb`; version from the existing AWS SDK BOM).

### 6.3 Workflows

All table writes use `--table-name vpnbot --region ${{ vars.CONFIG_BASE_REGION }}` (env
`CONFIG_TABLE_NAME: vpnbot`, `CONFIG_TABLE_REGION: ${{ vars.CONFIG_BASE_REGION }}`).

- New script `.github/scripts/region-item.sh <region> <stack-outputs.json>`: prints the DynamoDB item
  JSON of §5 (uses `jq`; `updatedAt` = current UTC time); exits non-zero if any of the four outputs
  (`EcsClusterName`, `EcsTaskDefinitionArn`, `SubnetId`, `SecurityGroupId`) is missing or empty.
- `deploy-vpn-ecs-resources.yml`, step "Update Task Configuration" → "Register region in DynamoDB"
  (same `if`): read the stack outputs into a local file and
  `aws dynamodb put-item --item "$(.github/scripts/region-item.sh …)"`. The S3 writes
  (`stack-outputs.json`, `supported-regions.txt`) and their env vars are removed.
- `delete-ecs-vpn-resources.yml`: new **first** step after credentials, "Remove region from DynamoDB":
  `aws dynamodb delete-item --key '{"pk":{"S":"REGION"},"sk":{"S":"<region>"}}'` (idempotent), so
  the bot stops offering the region before its stack goes. The S3 cleanup step and its env vars are removed.
- `deploy-tgbot-lambda.yml`: env `CONFIG_CACHE_ENABLED: ${{ vars.CONFIG_CACHE_ENABLED || 'false' }}`,
  passed as `EnvConfigCacheEnabled=…` in the parameter overrides.
- `deploy-tgbot-lambda.yml`: after the stack deploy, "Bootstrap root user in DynamoDB":
  `put-item` of `(USER, $TG_ROOT_USERNAME, permissions SS ["ROOT_ACCESS"])` with
  `--condition-expression 'attribute_not_exists(pk)'`; a `ConditionalCheckFailedException` is
  success (user exists). The S3 bootstrap steps (`user-permissions.json`, `supported-regions.txt`)
  and their env vars are removed.
- One-off migration is a **local script** the owner runs once (no workflow):
  `scripts/migrate-config-to-dynamodb.sh [--dry-run]` (needs the AWS CLI and `jq`; settings from env
  vars with defaults: `CONFIG_BUCKET=ecs-mgmt-tg-bot-euc1-s3`, `CONFIG_DIR=ecs-tailscale-node/config`,
  `ECS_STACK_NAME=vpn-ecs-resources-cfn`, `CONFIG_TABLE_NAME=vpnbot`, `CONFIG_TABLE_REGION=eu-central-1`).
  For each region in `supported-regions.txt`: reads the stack outputs with
  `aws cloudformation describe-stacks` in that region (the source of truth, not the S3 copy) →
  `region-item.sh` → `put-item`. For each entry of `user-permissions.json` with a non-empty list:
  `put-item (USER, name, permissions SS)`. Idempotent; prints every item; `--dry-run` prints without
  writing. Deleted in Deploy B.

### 6.4 Deploy A checklist (owner)

1. "Deploy VPN Configurer Lambda Resources" from `feature/infra-cleanup` (creates the table, IAM,
   env var; bootstraps root user).
2. Run `scripts/migrate-config-to-dynamodb.sh --dry-run`, then without `--dry-run`; check the table
   in the console (1 region, all users).
3. "VPN Bot Lambda CI-CD" from `feature/infra-cleanup` (new code).
4. Verify: home screen lists the regions; start/stop a node; `/supportedRegions`; an admin command
   that changes permissions; `DynamoDbMs` appears in metrics; no `S3Ms`; re-deliveries (if any)
   log "Skipping duplicated event".
5. Measure the cache: a few days with `CONFIG_CACHE_ENABLED=false`, then a few with `true` (GitHub
   variable + "Deploy VPN Configurer Lambda Resources"); compare `DynamoDbMs` and `TotalMs` per
   `UpdateKind`. Keep or retire the cache based on that (follow-up, not part of this phase).

## 7. Deploy B – No VPC

### 7.1 CloudFormation (`vpn-configurer-lambda.yml`)

- Lambda: remove `VpcConfig`, `FileSystemConfigs`, the `BOT_TOKEN` env var and the VPC/EFS entries
  of `DependsOn`; `Timeout: 600` → `30` (API Gateway gives up after 29 s).
- Delete resources: `VpnConfigurerVpc`, both subnets, `LambdaSecurityGroup`,
  `VpnConfigurerInternetGateway` + attachment, both route tables + associations + routes,
  `NatInstanceSecurityGroup`, `VpnConfigurerNatInstanceEIP`, NAT ENI + EIP association,
  `VpnConfigurerNatInstance`, `LambdaEfsStorage`, `LambdaEfsMountTarget`, `LambdaEfsAccessPoint`.
- Delete parameters: `ClientIPCIDR`, `NatInstanceAMI`, `NatInstanceSize`, `TelegramIpRanges`,
  `EnvTgBotToken`.
- `LambdaRole`: delete the S3 statement, the EFS statement and the VPC statement except
  `ec2:DescribeNetworkInterfaces` (used to read node public IPs).
- Expect a slow delete of subnets/security groups while AWS releases the Lambda's ENIs (up to ~40 min).
- Security note: the Lambda security group never filtered callers (API Gateway invokes the Lambda
  through the Lambda API, not the network); requests stay authenticated by the webhook secret token.

### 7.2 Node stack (`cloudformation/ecs-vpn-server.yml`)

`TailscaleNodeEcsTaskLogGroup.RetentionInDays: 1` → `7`.

### 7.3 Java

- `BotTokenResolver.resolve(String secretId)`: the secret id is required (blank →
  `IllegalStateException`); the env fallback is removed. `ConfigManager.getBotToken()` is removed.

### 7.4 Workflows

- `deploy-tgbot-lambda.yml`: drop `EnvTgBotToken=…` from the parameter overrides.
- Delete `scripts/migrate-config-to-dynamodb.sh`.
- `ci-cd.yml` keeps uploading the jar to the S3 bucket (the bucket stays; only its config folder goes).

### 7.5 Deploy B checklist (owner)

1. "Deploy VPN Configurer Lambda Resources", then "VPN Bot Lambda CI-CD" (both from the branch).
2. Run "Deploy Tailscale ECS Resources" for each region with CloudFormation on, Docker off (retention).
3. Verify as in §6.4 plus: Lambda has no VPC config; no NAT instance/EIP/EFS in the account.
4. Manual cleanup (any time after A is verified; nothing reads them any more): delete the S3 config
   folder files (`supported-regions.txt`, per-region stack outputs, `user-permissions.json`) and the
   legacy `vpntgbot-s3` bucket.

## 8. Deploy C – SnapStart

### 8.1 CloudFormation

- Lambda: `SnapStart: { ApplyOn: PublishedVersions }`.
- New `VpnConfigurerLambdaVersion` (`AWS::Lambda::Version`) and `VpnConfigurerLambdaAlias`
  (`AWS::Lambda::Alias`, `Name: live`, `FunctionVersion: !GetAtt VpnConfigurerLambdaVersion.Version`).
  CloudFormation only sets the alias's first version; the workflows (§8.2) move it afterwards.
- API Gateway integration `IntegrationUri` → the alias ARN; `ApiGatewayIamRole` resource list adds the
  alias ARN; rename the API deployment resource again (new date suffix) so the stage is redeployed.

### 8.2 Workflows

A published version freezes code **and** configuration (env vars, memory, timeout), so both workflows
end with the same step "Publish version and point alias `live`":
`aws lambda wait function-updated-v2 --function-name vpnbot` → `publish-version` →
`update-alias --name live --function-version <new>`.
- `ci-cd.yml`: after `update-function-code`.
- `deploy-tgbot-lambda.yml`: after the stack deploy (configuration changes go live).

### 8.3 SnapStart safety (verify and record in the decision log)

The static block runs once, before the snapshot. Check and keep it safe:
- No randomness/unique ids created at init (none found in `src/main/java` at spec time).
- The bot token read from Secrets Manager at init is frozen in the snapshot: a token change needs a
  new published version (the Lambda deploy workflow does that).
- SDK clients created at init reconnect after restore (AWS SDK v2 retries on stale connections);
  the Telegram client must not open connections during init.
- Time-based state (webhook-secret TTL cache) is computed per request with `Clock`.
- The Enhanced Client table schemas are built at init (reflection, ≈ hundreds of ms on a cold JVM) and
  are therefore part of the snapshot.

### 8.4 Deploy C checklist (owner)

1. "Deploy VPN Configurer Lambda Resources", then "VPN Bot Lambda CI-CD".
2. Verify: alias `live` points to the newest version; requests work; cold starts log `Restore Duration`
   instead of `Init Duration`. Compare cold-request time with the 2026-10-09 baseline (4.4–6.6 s).

## 9. Acceptance criteria

Deploy A:
- AC-A1 `DynamoDbTaskConfigService.getSupportedRegions`: queries `pk = REGION` on the configured
  table, follows pagination, maps `sk` to `Region`, skips unknown ids; empty table → empty list.
- AC-A2 `getTaskRuntimeParameters`: `GetItem` with the right key; maps all four attributes; missing
  item or attribute → `IllegalStateException`.
- AC-A3 `DynamoDbPermissionsService.getUserPermissions`: all users across pages; unknown permission
  names skipped; no users → empty map.
- AC-A4 `updateUserPermissions`: non-empty → `PutItem` with `SS` of enum names; null/empty →
  `DeleteItem`; `assignRolesToUser`/`deleteUser` (interface defaults) end in these calls.
- AC-A5 `DynamoDbUpdateEventsRegistry.register`: first call → `PutItem` with key, `expiresAt` =
  now + 86400 s, `receivedAt`, condition `attribute_not_exists(pk)`, returns `true`;
  `ConditionalCheckFailedException` → `false`; other exception → `true` and logged.
- AC-A6 Handler: a duplicate update is skipped before any routing; a new one is routed once.
- AC-A7 Every DynamoDB call is timed as `DYNAMODB`; EMF line has `DynamoDbMs` and no `S3Ms`.
- AC-A8 `ConfigManager.getConfigTableName()` reads `CONFIG_TABLE_NAME`, default `vpnbot`.
- AC-A9 `region-item.sh`: valid outputs → item with all §5 attributes; a missing/empty output →
  non-zero exit and no item (tested with a small bash test script, `.github/scripts/region-item-test.sh`).
- AC-A11 `migrate-config-to-dynamodb.sh --dry-run` against a fake `aws` (test script
  `scripts/migrate-config-to-dynamodb-test.sh`): prints one region item per listed region and one user
  item per non-empty user, writes nothing; without `--dry-run` it calls `put-item` for each.
- AC-A12 Cache flag: `isConfigCacheEnabled()` default `false`; flag off → the configurers return the
  DynamoDB services unwrapped; on → wrapped. `CachingPermissionsService`: second read within the TTL
  hits no delegate, after the TTL it does, an update invalidates; `CachedTaskConfigService` tests
  keep passing after the rename.
- AC-A13 Beans: `TableSchema.fromBean` maps each bean to exactly the §5 attribute names (`pk`, `sk`,
  camelCase attributes, `permissions` as `SS`) and back (`itemToMap` / `mapToItem` round trip).
  Service tests mock `DynamoDbTable<T>`; queries return `PageIterable.create(() -> List.of(Page.create(items)).iterator())`.
- AC-A10 No reference to S3 or EFS remains in `src/main/java`; `pom.xml` has `dynamodb-enhanced`, not `s3`.

Deploy B:
- AC-B1 `BotTokenResolver`: reads the secret; blank id → `IllegalStateException`.
- AC-B2 Template: no `AWS::EC2::*` / `AWS::EFS::*` resources, no `VpcConfig`/`FileSystemConfigs`,
  no `BOT_TOKEN`, `Timeout: 30`; `ec2:DescribeNetworkInterfaces` still allowed
  (checked by reading the template; `cfn-lint` if available).

Deploy C:
- AC-C1 Template: SnapStart on published versions, alias `live`, integration targets the alias,
  deployment resource renamed. AC-C2 Both workflows publish a version and move the alias.

## 10. Tasks and file ownership (per deploy)

| Task | Files |
|---|---|
| A1 Java implementation | `src/main/java/**`, `src/main/resources/application.yml`, `pom.xml` |
| A2 Java tests (independent, TDD) | `src/test/java/**`, `src/test/resources/**` |
| A3 Infra + workflows + scripts (tech lead) | `cloudformation/vpn-configurer-lambda.yml`, `.github/workflows/*`, `.github/scripts/*`, `scripts/*` |
| B1 Java + infra + workflows | `BotTokenResolver`, `ConfigManager`, handler, templates, workflows; tests in `src/test/java/**` |
| C1 Infra + workflows | `cloudformation/vpn-configurer-lambda.yml`, `ci-cd.yml`, `deploy-tgbot-lambda.yml` |
| Docs (tech lead) | this spec's decision log, `docs/roadmap.md`, `CLAUDE.md` |

## 11. Cost (eu-central-1 price list, 2026-10-10)

| Item | Today / month | After |
|---|---|---|
| NAT instance t3.micro ($0.012/h) | $8.76 | $0 |
| Public IPv4 of the NAT ($0.005/h) | $3.65 | $0 |
| NAT root volume (≈ 8 GB gp3, $0.0952/GB) | ≈ $0.76 | $0 |
| EFS, S3 config reads | ≈ $0 | $0 |
| DynamoDB (≈ 1 WRU + 1–2 RRU per request, ≈ 330 requests/month) | – | ≈ $0.0003 |
| SnapStart (no charge for Java) | – | $0 |

## 12. Decision log

- D-1 (review) One table with the record type as partition key instead of three tables: identical
  cost (no per-table charge), fewer resources; record types are readable as `pk` values.
- D-2 (review) Lock record type is `TG_UPDATE_LOCK` (written before processing, so "processed" would
  be wrong).
- D-3 (review) Config caches kept behind `CONFIG_CACHE_ENABLED` (default off) to measure their benefit
  with `DynamoDbMs`; retire them later if they don't pay off. The permissions cache gets a TTL.
- D-4 (review) Workflows switch to DynamoDB in Deploy A – no dual writes to S3. Rolling back to
  pre-A code means updating the S3 files by hand (a local sync script only if that is ever needed).
  The Lambda's S3 permission is removed only in Deploy B, so the old code keeps working during the
  Deploy A rollout.
- D-5 (review) The migration is a local script, not a workflow (run once by the owner).
- D-6 (review) DynamoDB Enhanced Client with one bean per record type over the single table, instead
  of the low-level client and a constants class. Cost until SnapStart (Deploy C): schema creation by
  reflection at init adds to cold starts; after C it is part of the snapshot.
- D-7 (review) Attribute names in camelCase (the Enhanced Client default: no per-field mapping);
  keys `pk`/`sk` in lower case.
