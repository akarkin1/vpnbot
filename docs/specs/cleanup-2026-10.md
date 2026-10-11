# Clean-up 2026.10 – system commands, no pending users, sealed UI actions

Status: draft for review, 2026-10-12. Branch `feature/cleanup-3.0`, version `2026.10-SNAPSHOT`
(calendar versioning from this release on; `2.0` = `release/2.0`, the last pre-UI version).

## 1. Goal

Remove what the button UI (Phases 2a–4) made redundant and what Phase 4 kept only for backward
compatibility, so that the code going into release 2026.10 has one way to do each thing:

1. **System commands only.** The text commands that duplicate a button are retired; `/help`,
   `/version`, `/supportedRegions` and `/describeRoles` stay as *system commands*. The command
   layer runs on `UiContext` and `UiMessenger`; `TgRequestContext` (the last static mutable state)
   and `BotCommunicator` are deleted.
2. **No pending users.** Every user is a `USER` record keyed by the Telegram user id. The
   `PENDING_USER` record type, the activation at first contact, the username fallback for node
   ownership (Phase 4 D-3) and the migration script are removed. Deployed only once no pending
   record is left (§11).
3. **Sealed `UiAction`.** One record per button action with its own encoding, an exhaustive
   pattern switch in the router; the callback-data wire format is unchanged.
4. **Small debts:** `listTasks` → `listNodes`, `UserSignupService` folded into
   `PermissionsService`, `EcsNode.cluster` dropped, unused message keys deleted.
5. **Infra naming:** the jar bucket is `LAMBDA_JAR_BUCKET` / `LambdaJarBucketName` (it holds the
   jar, nothing else); the stack-deploy workflow reads the version from `pom.xml` instead of an
   input; the Telegram command menu lists the system commands.

## 2. Non-goals

- Phase 5 (region requests), deep links, custom node names, reminders.
- The node agent, the ECS/EC2 layer beyond the `cluster` field, the DynamoDB layout beyond removing
  `PENDING_USER`, the roles/permissions model, the texts of the kept commands.
- A `/assignRoles` replacement: new users come in through *Request access*; an admin changes roles
  on the Users screen. Pre-registration by username is gone with the pending users (D-1).
- New dependencies.

## 3. Ground rules

Same as `docs/specs/node-lifecycle-2a.md` §3 (CLAUDE.md, existing patterns, exact contracts,
file ownership, `SPEC DEVIATION: <what> / <why> / <proposed option>` escalation). Every removal is a
deletion, not a `@Deprecated`: the bot has a handful of users and one deployer.

## 4. Behaviour

### 4.1 System commands

| Command | Kept? | Permission | Notes |
|---|---|---|---|
| `/help` | yes | none | lists the system commands the caller may use, as today |
| `/version` | yes | `ROOT_ACCESS` | unchanged |
| `/supportedRegions` | yes | `SUPPORTED_REGIONS` | unchanged |
| `/describeRoles` | yes | `USER_MANAGEMENT` | unchanged text; reads the role table from `RoleSet` |
| `/start`, `/menu` | yes (UI) | – | handled by `UiRouter` as today |
| `/runNodeIn`, `/listRunningNodes` | **retired** | – | home card / region buttons |
| `/assignRoles`, `/deleteUsers`, `/listRegisteredUsers` | **retired** | – | Users screen, Request access |

A retired or unknown command gets today's `${common.command.not-supported.error}` followed by the
`/help` list. Plain text (no leading `/`) never reaches the dispatcher: `UiRouter.canHandle` is true
for it and shows the home card, as today. `${common.syntax.not-supported.error}` is therefore dead
and is deleted.

**Replies** are `Screen`s sent through `UiMessenger.send(context, screen)`: HTML parse mode, params
HTML-escaped, translated to the caller's language. Each reply ends with the `[🏠 Menu]` row
(`CommonButtons.MENU`) so the user can get back to the UI (D-4). Command texts are built as
today (`StringBuilder` with `${key}` placeholders, no `%` in the text except via params); a
`Screen` with an empty button list is not used.

**Context.** `CommandDispatcher.handle(UiContext context, String text)`; `BotCommand.run(UiContext
context, List<String> args)` returns a `Screen`. The permission check uses `context.user()`. No
command reads anything static.

**Deleted:** `RunNodeCommand`, `ListNodesCommand`, `AssignRolesCommand`, `DeleteUsersCommand`,
`ListUsersCommand`, `UserArguments`, `dispatcher.response.*` (`CommandResponse`,
`TextCommandResponse`, `EmptyResponse`), `exception.InvalidCommandException`,
`exception.CommandExecutionFailedException`, `auth.UnauthorizedRequestException` (the dispatcher
answers `${user.not-authorized.error}` directly), `tg.BotCommunicator`, `tg.TgRequestContext`,
`ui.NodeLauncher` + `UiComponents.nodeLauncher()` **if** `LaunchController` is its only remaining
user (then `LaunchController` keeps the logic and `UiConfigurer` returns the router only; otherwise
keep it – note the outcome in the decision log), and their tests. Message keys: every
`command.run-node.*`, `command.list-nodes.*`, `command.assign-roles.*`, `command.delete-users.*`,
`command.list-users.*`, `common.node.*`, `common.region.*`, `common.command.invalid-syntax.message`,
`common.command.description.message`, `common.syntax.not-supported.error` key, in both properties
files (AC-2 verifies by grep).

### 4.2 Lambda handler

`handleUpdate(update)`:

1. dedup (`EVENTS_REGISTRY.register`) – unchanged;
2. `UiContext context = UiContext.fromUpdate(update)`; an update with neither message nor callback
   is logged and dropped (today: `TgRequestContext` with nulls);
3. `UI_ROUTER.canHandle(update)` → `UI_ROUTER.handle(update)`; else
   `SYSTEM_COMMANDS_DISPATCHER.handle(context, update.getMessage().getText())`.

Error handling: a failure before the context exists (authentication, JSON parsing) is logged and
answered to API Gateway only; there is no chat to write to. Today's code sends
`${bot.internal.error}` through `BotCommunicator`, which reads the chat id from the static
`TgRequestContext` – i.e. from the *previous* request of the same container (D-5: a latent bug that
goes with the static state). A failure after the context exists is answered with
`ErrorScreen.generic()` to `context` via `UiMessenger` (same screen `UiRouter` uses).
`${bot.internal.error}` is deleted if nothing else uses it.

### 4.3 No pending users

- `TgUser(long id, String username)`: the id is mandatory. Equality and hash code by id only (a
  renamed user is the same user; D-2). `key()` returns the decimal id. `pending()` is removed.
- `PermissionsService` (now also holding the former `UserSignupService` methods, §7):
  `getUsers()` reads `USER` records only; `updateUserPermissions(TgUser, Set<Permission>)` writes
  or deletes the `USER` record keyed by the id (username attribute: the given one, or the stored
  one when null – unchanged); `findByUsername` is removed (no caller left).
- `DynamoDbPermissionsService(users, metrics)`; `ConfigTables(regions, users, updateLocks,
  accessRequests)`; `PendingUserRecord` and its test are deleted; `PermissionsServiceConfigurer`
  loses the pending table.
- `PendingUserActivator` and its call in the handler are deleted. `WhiteListAuthorizer` is
  unchanged (already read-only since Phase 4 D-24).
- `NodeAccess.isOwner`: `RunBy` equals the user id, nothing else (Phase 4 D-3 removed).
- Users screen: the `pending` suffix and `${ui.users.pending}` go; `UserRow.editable` unchanged.
- `scripts/migrate-pending-users.sh`, its test, the `java-tests.yml` step and the CLAUDE.md line go.
- The root bootstrap step in `deploy-tgbot-lambda.yml` stays (keyed by `TG_ROOT_USER_ID`).
- A `USER` record whose key is not a number is still skipped with a warning (Phase 4 D-14).

### 4.4 Sealed `UiAction`

```
package org.github.akarkin1.ui;
public sealed interface UiAction {
  record Home() implements UiAction {}                       // HOME
  record Help() implements UiAction {}                       // HELP
  record Run(String regionId) implements UiAction {}         // RUN:<regionId>
  record RunNew(String regionId) implements UiAction {}      // RUN_NEW:<regionId>
  record Use(NodeRef node) implements UiAction {}            // USE:<regionId>:<taskId>   (NodeRef.encode())
  record Stop(NodeRef node) implements UiAction {}           // STOP:<…>
  record ConfirmStop(NodeRef node) implements UiAction {}    // STOP_CONFIRM:<…>
  record RequestAccess() implements UiAction {}              // REQUEST_ACCESS
  record AccessToggle(long userId, Set<UserRole> selected, UserRole role) implements UiAction {} // ACCESS_TOGGLE:<id>:<mask>:<ROLE>
  record AccessGrant(long userId, Set<UserRole> selected) implements UiAction {}   // ACCESS_GRANT:<id>:<mask>
  record AccessDecline(long userId) implements UiAction {}   // ACCESS_DECLINE:<id>
  record Users() implements UiAction {}                      // USERS
  record ShowUser(long userId) implements UiAction {}        // USER:<id>
  record ToggleRole(long userId, UserRole role) implements UiAction {}  // USER_ROLE:<id>:<ROLE>
  record DeleteUser(long userId) implements UiAction {}      // USER_DEL:<id>
  record ConfirmDeleteUser(long userId) implements UiAction {} // USER_DEL_CONFIRM:<id>

  String encode();                                   // the callback data; ≤ 64 UTF-8 bytes or IllegalArgumentException
  static Optional<UiAction> decode(String data);     // tag before the first ':' → that record's parser; empty for anything invalid
}
```

- All records are nested in `UiAction.java` (same compilation unit, so no `permits` clause).
- Each record validates in its compact constructor (`regionId` not blank; `userId > 0`; a `NodeRef`
  that `NodeRef.encode()`/`parse` round-trips; the mask `RoleSet.isValid`) and owns its `encode()`
  and a `static Optional<X> parse(String arg)`. `RoleSet.encode/decode` are used only by the two
  access records. The `Type` enum, the regexes and the `Optional` accessors (`nodeRef()`,
  `userId()`, `userKey()`, `roleMask()`, `role()`) disappear.
- `decode`: split at the first `:` into tag and argument; one `Map<String, Function<String,
  Optional<? extends UiAction>>>` from tag to parser; a tag without argument (`HOME`) rejects a
  non-null argument and vice versa. Unknown tag, bad argument → empty, and the router falls back to
  refreshing the home card, as today.
- **Wire format unchanged**, tag names included, so buttons in messages sent before the deploy keep
  working. `USER:<@username>` keys can no longer appear (no pending users); such old data decodes to
  empty → home refresh.
- `UiRouter.dispatch` becomes `switch (action) { case Home() -> …; case Run(var regionId) -> …;
  case AccessToggle(var id, var selected, var role) -> …; … }` with no default branch; the
  `grant` helper's "mask 0 → ignore" check becomes `selected.isEmpty()` (same behaviour as Phase 4
  D-22). `AccessController.toggle/grant` take `Set<UserRole>` instead of an int mask.
- Screens keep calling static factories on the interface (`UiAction.run(regionId)` etc.) so they
  do not change, or use the record constructors – the implementer picks one and uses it everywhere.

### 4.5 Renames and small debts

- `TailscaleNodeService.listTasks(String userTgId)` → `listNodes(String userId)`; callers:
  `HomeController`, `LaunchController`, `SnapStartPrimer`.
- `UserSignupService` is deleted; `updateUserPermissions`, `deleteUser`, `assignRolesToUser` move to
  `PermissionsService` (same signatures); `describeRoles()` becomes `RoleSet.describe()`
  (`Map<UserRole, List<Permission>>`, same content) and `DescribeRolesCommand` has no dependency.
- `EcsNode.cluster` is removed (set in `EcsManagerImpl`, read nowhere).
- `HelpCommand` keeps its dependency on the dispatcher for the command list.

### 4.6 Infra and workflows

- Repository variable `LAMBDA_JAR_BUCKET` replaces `S3_CONFIG_BUCKET_NAME` (created by the owner
  before the deploy, old one deleted after); `ci-cd.yml` and `deploy-tgbot-lambda.yml` read it; the
  stack parameter is `LambdaJarBucketName` (description: "S3 bucket holding the Lambda jar").
- `deploy-tgbot-lambda.yml` drops the `lambda-source-version` input; after checkout it sets up JDK
  21 (as `ci-cd.yml`) and reads `POM_VERSION=$(mvn -B -q help:evaluate -Dexpression=project.version
  -DforceStdout)` for `LambdaSourceVersion`. `pom.xml` is the only place holding the version.
- "Register Telegram bot commands" registers `menu`, `help`, `version`, `supportedRegions`,
  `describeRoles` (EN and RU descriptions taken from the `command.*.description.message` texts).
- `java-tests.yml`: the migration script test step goes; the other two shell tests stay.

## 5. Data model (table `vpnbot`)

`PENDING_USER` records are no longer read or written. The deploy precondition (§11) is that none
exist; a leftover would be invisible to the new code (its user has no access until an admin grants
it through Request access).

## 6. Texts

No new keys. Deleted keys: §4.1 and `ui.users.pending`. RU file stays ASCII-escaped.

## 7. Contracts

```java
// dispatcher
public class CommandDispatcher {
  public CommandDispatcher(UiMessenger messenger, Authorizer authorizer);   // registers /help itself
  public void registerCommand(String name, BotCommand command);
  public void handle(UiContext context, String text);                       // text starts with '/'
  public String supportedCommands(TgUser user);                             // for /help
}
public interface BotCommand {
  Screen run(UiContext context, List<String> args);
  String getDescription();
  List<Permission> getRequiredPermissions();
}
// auth
public record TgUser(long id, String username) { public String key(); }   // equals/hashCode by id
public interface PermissionsService {
  Map<TgUser, List<Permission>> getUsers();
  void updateUserPermissions(TgUser user, Set<Permission> permissions);   // null/empty deletes
  default void deleteUser(TgUser user);
  default void assignRolesToUser(TgUser user, Set<UserRole> roles);
}
public final class RoleSet { … ; static Map<UserRole, List<Permission>> describe(); }
public class DynamoDbPermissionsService(DynamoDbTable<UserRecord> users, RequestMetrics metrics)
public record ConfigTables(regions, users, updateLocks, accessRequests)
// ui
public sealed interface UiAction { … }                                   // §4.4
public class UiRouter(home, launch, node, access, users, messenger, errorScreen)   // unchanged
AccessController.toggle(UiContext, Integer messageId, long userId, Set<UserRole> selected, UserRole role)
AccessController.grant(UiContext, Integer messageId, long userId, Set<UserRole> roles)
UsersController.showUser/toggleRole/confirmDelete/delete(UiContext, Integer messageId, long userId[, UserRole role])
// tailscale
List<EcsNode> listNodes(String userId);   // null: all nodes
// ecs
EcsNode: id, hostName, state, publicIp, location, region, runBy, runByName, chatId, languageCode
```

## 8. Acceptance criteria

- AC-1 `CommandDispatcher`: a registered command runs with the given context and its reply is sent
  via `UiMessenger.send(context, screen)` with the Menu row; a command the user lacks a permission
  for → `${user.not-authorized.error}`; an unknown or retired command → not-supported text plus the
  `/help` list of the commands the caller may use; nothing is read from static state.
- AC-2 No reference to a deleted class or message key remains: `grep` for `TgRequestContext`,
  `BotCommunicator`, `PENDING_USER`, `PendingUser`, `findByUsername`, `pending()`, `listTasks(`,
  `UserSignupService`, `getCluster` in `src` and `.github` is empty; every `command.*`, `common.*`,
  `user.*`, `bot.*` and `ui.*` key in `messages.properties` is referenced from Java; both properties
  files have the same key set.
- AC-3 Handler: a request whose update has no message and no callback is dropped with a log line;
  an exception from the router or the dispatcher answers the caller's chat with the generic error
  screen (unit-testable part: the dispatcher's own error path; the handler stays static wiring).
- AC-4 `TgUser` equality by id; `key()` is the id; `DynamoDbPermissionsService.getUsers()` queries
  `USER` only (one `FakeTable` query); a non-numeric key is skipped with a warning.
- AC-5 `NodeAccess.isOwner`: id match only; a node whose `RunBy` is the username is not owned.
- AC-6 `UiAction`: every record round-trips `encode` → `decode`; the encoded strings equal the
  Phase 4 strings for the same inputs (table test: `HOME`, `RUN:eu-central-1`,
  `USE:eu-central-1:<32 hex>`, `ACCESS_TOGGLE:123:5:NODE_ADMIN`, `ACCESS_GRANT:123:0`,
  `USER_ROLE:123:READ_ONLY`, …); invalid data (`USER:@alice`, `ACCESS_GRANT:123:9`, `RUN:`,
  `HOME:x`, 65-byte data on encode) → empty / `IllegalArgumentException`; the router dispatches
  each record to the right controller method with the typed arguments.
- AC-7 Workflows: `deploy-tgbot-lambda.yml` has no `lambda-source-version` input and passes the pom
  version; both workflows use `LAMBDA_JAR_BUCKET`; the stack parameter is `LambdaJarBucketName`;
  `setMyCommands` lists the five commands in EN and RU; `java-tests.yml` runs two shell tests.
- AC-8 `mvn -B verify` green; the three remaining shell tests green; properties ASCII-only.

## 9. Definition of done

AC-1…AC-8 pass; CLAUDE.md and the roadmap describe the new state; the §11 deploy succeeded and the
smoke test passed; no `TODO`.

## 10. Tasks and file ownership

| Task | Files |
|---|---|
| T1 System commands + handler (Java) | `dispatcher/**`, `tg/BotCommunicator`, `tg/TgRequestContext`, `exception/**`, `auth/UnauthorizedRequestException`, `TailscaleVpnLambdaHandler`, `ui/NodeLauncher`, `ui/UiComponents`, `ui/UiConfigurer`, messages (command/common keys) |
| T2 T1 tests | `src/test/java/**/dispatcher/**`, `tg/**`, `ui/UiConfigurerTest` |
| T3 No pending users + renames (Java) | `auth/**`, `dynamodb/PendingUserRecord`, `ConfigTables`, `ui/controller/NodeAccess`, `UsersController`, `ui/screen/UsersScreen`, `HomeController`, `LaunchController`, `startup/SnapStartPrimer`, `tailscale/**`, `ecs/EcsNode`, `EcsManagerImpl`, `scripts/migrate-pending-users*`, messages (`ui.users.pending`) |
| T4 T3 tests | `src/test/java/**` for T3 (incl. `FakePermissionsService`, `FakeTable` users) |
| T5 Sealed UiAction (Java) | `ui/UiAction`, `ui/UiRouter`, `ui/controller/AccessController`, `ui/screen/*` factories |
| T6 T5 tests | `ui/UiActionTest`, `UiRouterTest`, `AccessControllerTest`, screen tests touching actions |
| H1 Infra + docs (tech lead) | `.github/workflows/*.yml`, `cloudformation/vpn-configurer-lambda.yml`, `CLAUDE.md`, `docs/roadmap.md`, this spec |

Order: T1/T2 → T3/T4 → T5/T6 (each pair merged before the next starts: the handler, `TgUser` and
`UiAction` are touched by more than one task) → H1. Each task is one commit series on
`feature/cleanup-3.0`; the tech lead merges and re-runs the full build after each.

## 11. Deploy checklist (owner)

1. **Precondition – no pending users left** (wait a day or two after the Phase 4 deploy):
   ```bash
   aws dynamodb query --table-name vpnbot --region eu-central-1 \
     --key-condition-expression 'pk = :p' --expression-attribute-values '{":p":{"S":"PENDING_USER"}}' \
     --query 'Items[].sk.S' --output text
   ```
   Empty output, or delete the leftovers by hand after deciding about those people (they can use
   Request access later).
2. Create the repository variable `LAMBDA_JAR_BUCKET` = `ecs-mgmt-tg-bot-euc1-s3`.
3. "VPN Bot Lambda CI-CD" from `feature/cleanup-3.0`: uploads `vpntgbot/vpnbot-2026.10-SNAPSHOT.jar`
   and moves `live`.
4. "Deploy VPN Configurer Lambda Resources" (region eu-central-1): renamed parameter, version from
   the pom, `setMyCommands`. Expected stack change: the Lambda `Code` key (already uploaded in
   step 3), nothing else.
5. Delete the repository variable `S3_CONFIG_BUCKET_NAME`.
6. Smoke test: `/help` lists the system commands; `/runNodeIn eu-central-1` answers "unsupported"
   with the list; `/version`; home card → start a node → stop it; Users screen without a pending
   suffix; a second account: Request access → grant → regions shown.
7. Merge; release when Phase 5 lands (pom `2026.10` or the month it happens, tag `v<version>`).

## 12. Decisions (review)

- D-1 Pending users go entirely, `/assignRoles` with them; the only way in is Request access, the
  only way to change roles is the Users screen. Pre-registration by username is not replaced.
- D-2 `TgUser` keeps equality by id although both components are now always present: a user who
  changes their username must still match the stored record.
- D-3 `/help`, `/version`, `/supportedRegions`, `/describeRoles` are the *system commands* (owner's
  name); they stay on the `CommandDispatcher`, which becomes a thin router over `UiContext` and
  `UiMessenger`.
- D-4 System-command replies get the `[🏠 Menu]` row like every other screen. Small behaviour
  change, accepted for consistency; revert to a buttonless screen if the owner objects.
- D-5 The static `TgRequestContext` is deleted rather than kept for the pre-context error path:
  that path wrote `${bot.internal.error}` to whatever chat id the previous request of the same
  container had left in the static fields. Such failures are now logged only.
- D-6 `UiAction` records are nested in one file; the wire format and tag names are frozen so
  pre-deploy messages keep working.
- D-7 Calendar versioning `YYYY.MM[.N]` from this release; `2.0` keeps its name.
- D-8 The jar bucket variable is renamed rather than kept: its old name (`S3_CONFIG_BUCKET_NAME`)
  caused a failed deploy on 2026-10-11 when it was taken for a Phase 3 leftover.

## 13. Decision log

(filled during implementation: `SPEC DEVIATION`s and their resolutions)
