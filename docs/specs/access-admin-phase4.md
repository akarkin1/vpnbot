# Spec: Phase 4 – Access & admin (+ housekeeping)

Status: draft for review · Branch: `feature/access-admin` (based on `main`) · Roadmap: `docs/roadmap.md`

## 1. Goal

1. **Identity by Telegram user id.** Permissions and node ownership are keyed by the immutable user id
   instead of the username, so a renamed account keeps its access and a freed username cannot be
   taken over by someone else. Usernames stay for display and for the admin text commands.
2. **Request access.** A user without access taps `🙋 Request access`; every admin gets a message with
   role buttons; the requester is notified of the decision. No more out-of-band "ask @karkin_ai".
3. **Users screen.** Admins manage users from the UI: a list with roles and a 🗑 button per user.
4. **Housekeeping** carried over from Phase 3: Java tests on pull requests, node image on Python 3.11,
   unused GitHub variables removed.

## 2. Non-goals

- No change to node start/stop/reuse behaviour, screens of Phases 1–3, metrics, infrastructure or IAM.
- No change to the admin text commands' behaviour: pre-registering a user by username keeps working
  (§4.6); the request-access flow is an addition, not a replacement.
- No new dependencies. No refactoring beyond what this spec lists.

## 3. Ground rules

Same as `docs/specs/node-lifecycle-2a.md` §3 (CLAUDE.md, existing patterns, exact contracts,
file ownership, `SPEC DEVIATION: <what> / <why> / <proposed option>` escalation).

## 4. Behaviour

### 4.1 Identity

- `org.github.akarkin1.auth.TgUser(Long id, String username)` (record; one of the two may be null:
  requests always carry the id; a pre-registration carries only the username). `TgUser.key()` = the
  record key: the id as a decimal string when known, else the username.
  `UiContext` gains `Long userId` (from `User.getId()`), and `UiContext.user()` returns the `TgUser`.
  `TgRequestContext` (legacy, used by text commands) gains `getUser()` returning the same.
- `Authorizer.hasPermission(TgUser user, Permission permission)` replaces the username variant;
  every caller passes the `TgUser` of the request. The root/"all nodes" checks in `HomeController`,
  `NodeAccess`, `ListNodesCommand` and `CommandDispatcher.checkUserPermissions` use it.
- `PermissionsService`:
  - `Map<String, UserEntry> getUsers()` keyed by user id (decimal string), `UserEntry(String username,
    List<Permission> permissions)`; replaces `getUserPermissions()` (and `UserPermissionsProvider`).
  - `updateUserPermissions(TgUser user, Set<Permission> permissions)`: non-empty → put the record
    keyed by `user.key()` with the username as attribute (null username keeps the stored one);
    null/empty → delete. `assignRolesToUser`/`deleteUser` defaults take a `TgUser` likewise.
  - `Optional<TgUser> findByUsername(String username)`: the user with that username (attribute or
    key, case-insensitive, without `@`), for the text commands and the Users screen.
- `UserRecord`: sk = user id (decimal string) for users who have contacted the bot, or the username
  for **pending** users (pre-registered by an admin, and the records existing before this phase);
  attributes `username` (S, nullable), `permissions` (SS). `isUsernameKey(sk)` = sk is not all digits.
  **Migration on first contact** (D-2): `WhiteListAuthorizer`, on a request by a user with no id-keyed
  record, looks for a record keyed by the username; if found it writes the id-keyed record (same
  permissions, `username` attribute) and deletes the old one, in that order, then answers from the new
  record. Pre-registrations therefore work as today, with the record re-keyed once the user shows up.
- The workflow bootstrap of the root user (`deploy-tgbot-lambda.yml`) keeps writing the username-keyed
  record (`TG_ROOT_USERNAME`); it is migrated at the root's next request. Its condition
  `attribute_not_exists(pk)` still prevents overwriting.
- Node ownership: the `RunBy` tag holds the user id; new tag `RunByName` (config `run-by-name-tag`)
  holds the username for display (`NodeOwner` gains `userId`; `TaskInfo` gains `runByName`).
  `NodeAccess.isOwner` matches `runBy` against the id or, during the transition, the username (D-3).
  `TailscaleNodeService.listTasks(String userTgId)` receives the id. Screens that show the owner
  (`confirmStop`, home "all nodes") use `runByName`, falling back to `runBy`.

### 4.2 Access request – requester side

Unknown user = no permissions at all (`getUsers()` has no entry with ≥ 1 permission for the id, after
the lazy-migration check). Their home screen (`/start`, `/menu`, 🏠) shows the existing
`${ui.home.no-access}` text (reworded, §4.7) with one row `[🙋 ${ui.button.request-access}]`
(`UiAction.requestAccess()`, type `REQUEST_ACCESS`).

Tap → `AccessController.request(context, messageId)`:
1. If the user now has permissions (granted meanwhile) → `homeController.refreshHome`.
2. If an `ACCESS_REQUEST` record exists for the id → edit to `accessScreens.alreadyRequested()`
   (`${ui.access.already-requested}` + `[🏠 Menu]`).
3. Else write the record (§5), edit to `accessScreens.requested()` (`${ui.access.requested}` +
   `[🏠 Menu]`) and send `accessScreens.adminRequest(request)` to every admin (§4.3). If no admin can
   be notified (none known) the request is still recorded; log a warning.

### 4.3 Access request – admin side

Admins = users whose permissions include `USER_MANAGEMENT` or `ROOT_ACCESS`. A private chat's id
equals the user id, so the notification goes to chat id = admin user id with the admin's language
(the user record has none → the bot's default, D-4). The message:
`🙋 <b>First Last</b> (@username, id) ${ui.access.asks}` with rows
`[🚀 ${ui.role.node-admin}] [👀 ${ui.role.read-only}]` and `[✖ ${ui.button.decline}]`
(`UiAction.grant(userId, UserRole)` → `GRANT:<id>:<ROLE>`, `UiAction.decline(userId)` →
`DECLINE:<id>`; `USER_ADMIN` is not offered as a button – it stays a text-command role).

Grant/decline tap → `AccessController.decide(context, messageId, userId, Optional<UserRole>)`:
1. Caller must have `USER_MANAGEMENT` (or root), else edit to `accessScreens.notAllowed()`.
2. Read the request record; if missing → edit to `accessScreens.alreadyHandled()`
   (`${ui.access.already-handled}`), done.
3. Grant: `assignRolesToUser(TgUser(userId, request.username), Set.of(role))`; decline: nothing.
4. Delete the request record; edit the admin's message to `accessScreens.granted(request, role)` /
   `accessScreens.declined(request)` (no buttons); send the requester (chat id = user id, language
   from the record) `accessScreens.accessGranted(role)` (`${ui.access.granted}` + `[🏠 Menu]`) or
   `accessScreens.accessDeclined()` (`${ui.access.declined}`, no buttons). A failing requester
   notification is logged, not an error.

### 4.4 Users screen

Home screen: for users with `USER_MANAGEMENT` (or root) a row `[👥 ${ui.button.users}]`
(`UiAction.users()`, `USERS`) between the stop rows and Refresh/Help.

**List** – `UsersController.show(context, messageId)` → `usersScreen.list(model)`: title
`${ui.users.title}`, one button per user, sorted by username then key, labelled
`@username · <roles>` (or `id · <roles>` without a username; a pending user gets the suffix
`· ${ui.users.pending}`; `RoleFormat.describe`: the `UserRole`s whose permission sets are covered,
root → `${ui.role.root}`, otherwise the permission names), each `UiAction.user(key)` (`USER:<key>`,
key = id or `@username`); last row `[🏠 Menu]`. Users are referenced by their record key in every
callback of this screen (`<key>` below), so pending users can be edited and removed too.

**User card** – `UsersController.showUser(context, messageId, key)` → `usersScreen.user(row)`:
`👤 <b>@username</b> (id or ${ui.users.pending})\n<roles>` and, unless the target is a root user or the caller:
- role toggles, one row `[✅|☐ ${ui.role.node-admin}] [✅|☐ ${ui.role.read-only}]` and one row
  `[✅|☐ ${ui.role.user-admin}]` (`UiAction.toggleRole(key, role)` → `USER_ROLE:<key>:<ROLE>`); a role is
  ticked when its permission set is a subset of the user's permissions;
- `[🗑 ${ui.button.remove}]` (`USER_DEL:<key>`);
- `[◀ ${ui.button.users}]` (`USERS`).
For a root user or the caller the card has only the back button.

Toggle → `UsersController.toggleRole(context, messageId, key, role)`: permission check; refuse for
root users and the caller (card unchanged); compute the ticked set after the toggle; if it is empty →
`usersScreen.confirmDelete(row)` (same as 🗑); else `assignRolesToUser(row.user(),
tickedRoles)` (the union of the roles' permissions, replacing the previous permissions) and show the
card again. The affected user is not notified (D-8).

🗑 → `usersScreen.confirmDelete(row)`: `${ui.users.confirm-delete}` with
`[✅ ${ui.button.confirm-delete}]` (`USER_DEL_CONFIRM:<key>`) `[✖ ${ui.button.cancel}]` (`USER:<key>`).
Confirm → `UsersController.delete(...)`: permission check, refuse root/self even on a forged callback,
`deleteUser(row.user())`, back to the list.

### 4.5 Routing

`UiAction.Type` += `REQUEST_ACCESS, GRANT, DECLINE, USERS, USER, USER_ROLE, USER_DEL, USER_DEL_CONFIRM`;
`UiAction.userId()` (`Optional<Long>`) and `UiAction.role()` (`Optional<UserRole>`) for the typed
ones. `UiRouter` dispatches them to `AccessController` / `UsersController`. Callback data stays under
64 bytes (`USER_ROLE:@<32-char username>:NODE_ADMIN` = 54, the longest).

### 4.6 Text commands (existing, behaviour unchanged)

`/assignRoles`, `/deleteUsers`, `/listRegisteredUsers` keep their syntax and behaviour. Internally a
`@username` argument is resolved with `findByUsername`; an unknown username in `/assignRoles` creates a
pending (username-keyed) record exactly as today. `/listRegisteredUsers` prints `@username (id): roles`,
or `@username (pending): roles` for a pending user. A numeric id is accepted too.

### 4.7 Texts (EN / RU; RU as `\uXXXX` in the properties files)

| Key | EN | RU |
|---|---|---|
| `ui.home.no-access` | You don't have access to this bot yet. | У вас пока нет доступа к этому боту. |
| `ui.button.request-access` | Request access | Запросить доступ |
| `ui.access.requested` | Your request was sent to the administrators. You'll get a message here once it's decided. | Запрос отправлен администраторам. Вы получите сообщение, когда он будет рассмотрен. |
| `ui.access.already-requested` | Your request is already waiting for a decision. | Ваш запрос уже ожидает решения. |
| `ui.access.asks` | asks for access to the bot. | просит доступ к боту. |
| `ui.access.already-handled` | This request has already been handled. | Этот запрос уже обработан. |
| `ui.access.granted-admin` | granted: %s | доступ выдан: %s |
| `ui.access.declined-admin` | declined. | отклонён. |
| `ui.access.granted` | Access granted: %s. Welcome! | Доступ выдан: %s. Добро пожаловать! |
| `ui.access.declined` | Your access request was declined. | Ваш запрос на доступ отклонён. |
| `ui.button.decline` | Decline | Отклонить |
| `ui.role.node-admin` | VPN user | Пользователь VPN |
| `ui.role.read-only` | Read-only | Только просмотр |
| `ui.role.user-admin` | User admin | Администратор пользователей |
| `ui.role.root` | Root | Root |
| `ui.button.users` | Users | Пользователи |
| `ui.users.title` | Users | Пользователи |
| `ui.users.pending` | pending | ожидает |
| `ui.users.confirm-delete` | Remove %s from the bot? | Удалить %s из бота? |
| `ui.button.confirm-delete` | Yes, remove | Да, удалить |
| `ui.button.remove` | Remove | Удалить |
| `ui.button.cancel` | Cancel | Отмена |
| `ui.users.not-allowed` | You are not allowed to manage users. | Вы не можете управлять пользователями. |

`%s` are params (HTML-escaped where dynamic); emoji live in Java.

## 5. Data model additions (table `vpnbot`)

| pk | sk | Attributes |
|---|---|---|
| `USER` (changed) | user id, or username while pending | `username` (S, nullable), `permissions` (SS) |
| `ACCESS_REQUEST` (new) | user id | `username` (S, nullable), `firstName` (S), `languageCode` (S), `requestedAt` (N, epoch ms), `expiresAt` (N, TTL = requestedAt + 7 days) |

Bean `AccessRequestRecord` in `dynamodb` (same style as the others, keys on Lombok getters);
`ConfigTables` gains `accessRequests()`. Service `auth.DynamoDbAccessRequestService implements
AccessRequestService` (`find(long id)`, `create(AccessRequest)`, `delete(long id)`), timed as
`DYNAMODB`. A request that is never decided expires after 7 days (the user can ask again).

## 6. Housekeeping

### 6.1 Java tests on pull requests
`.github/workflows/java-tests.yml`: `on: pull_request` (paths `src/**`, `pom.xml`, `.github/scripts/**`,
the workflow itself) and `workflow_dispatch`; JDK 21 Corretto (as `ci-cd.yml`); steps: `mvn -B verify`,
`.github/scripts/region-item-test.sh`, `.github/scripts/publish-live-version-test.sh`. No AWS access.

### 6.2 Node image on Python 3.11
`docker/Dockerfile`: `dnf install -y python3.11 python3.11-pip tailscale`, `pip3.11 install`, `CMD
["python3.11", "-m", "node_agent"]`; `node-agent-tests.yml` `python-version: '3.11'`; CLAUDE.md
"Python 3.11". `docker/requirements.txt` pins unchanged unless `pip3.11` rejects one (then re-pin to the
newest release supporting 3.11 and record it). Deployed with "Deploy Tailscale ECS Resources" (Docker
on) per region.

### 6.3 Repository variables (manual, owner)
Delete `S3_CONFIG_DIR`, `S3_SUPPORTED_REGIONS_FILE_NAME`, `S3_VPN_SERVER_STACK_OUTPUTS_FILE_NAME`,
`S3_USER_PERMISSIONS_FILE_NAME` in the repository settings; no workflow reads them.

## 7. Contracts

```java
// auth
public record TgUser(Long id, String username) { public String key(); }   // id or username, one may be null
public interface Authorizer { boolean hasPermission(TgUser user, Permission permission); }
public record UserEntry(String username, List<Permission> permissions) {}
public interface PermissionsService extends UserSignupService {
  Map<String, UserEntry> getUsers();                       // key: user id as decimal string
  Optional<TgUser> findByUsername(String username);
}
UserSignupService: updateUserPermissions(TgUser, Set<Permission>); deleteUser(TgUser); assignRolesToUser(TgUser, Set<UserRole>)
public record AccessRequest(long userId, String username, String firstName, String languageCode, long requestedAt) {}
public interface AccessRequestService { Optional<AccessRequest> find(long userId); void create(AccessRequest r); void delete(long userId); }
public final class RoleFormat { public static String describe(List<Permission> p, Translator t); }   // or Screen-side

// ui
UiContext(Long chatId, Long userId, String username, String firstName, String languageCode) + TgUser user()
UiAction: requestAccess(), grant(long userId, UserRole role), decline(long userId), users(), user(String key), toggleRole(String key, UserRole), deleteUser(String key), confirmDeleteUser(String key)
         + Optional<Long> userId(), Optional<String> userKey(), Optional<UserRole> role()
// ui.controller
public class AccessController { request(UiContext, Integer messageId); decide(UiContext, Integer messageId, long userId, Optional<UserRole> role); }
public class UsersController  { show(UiContext, Integer messageId); showUser(UiContext, Integer messageId, String key); toggleRole(UiContext, Integer messageId, String key, UserRole role); confirmDelete(UiContext, Integer messageId, String key); delete(UiContext, Integer messageId, String key); }
// ui.screen
public class AccessScreens { requested(); alreadyRequested(); adminRequest(AccessRequest); granted(AccessRequest, UserRole); declined(AccessRequest); alreadyHandled(); accessGranted(UserRole); accessDeclined(); notAllowed(); }
public class UsersScreen   { list(UsersModel); user(UserRow); confirmDelete(UserRow); notAllowed(); }
public record UsersModel(List<UserRow> users) {}   public record UserRow(TgUser user, List<Permission> permissions, boolean editable) { boolean pending(); }   // editable = not root, not the caller
HomeModel: + boolean canManageUsers, boolean hasAccess
// tailscale / ecs
NodeOwner(long userId, String username, Long chatId, String languageCode); TaskInfo + runByName; EcsConfiguration + runByNameTag
```

## 8. Acceptance criteria

- AC-1 `UiContext.fromUpdate` carries the user id for messages and callbacks; `user()` matches.
- AC-2 `WhiteListAuthorizer`: id-keyed record → answers from it; no id record but username record →
  migrates (put new, delete old, in order) and answers; neither → false; auth disabled → true; root
  implies every permission.
- AC-3 `DynamoDbPermissionsService`: `getUsers` maps sk→key and `username` (pending = username key);
  `updateUserPermissions` writes under `user.key()` and keeps the stored username when the given one
  is null; `findByUsername` matches the attribute or a username key, case-insensitive, strips `@`;
  delete on empty permissions.
- AC-4 `AccessController.request`: the three branches of §4.2; the admin notification goes to each
  admin's user id as chat id, not to non-admins; records the request with `expiresAt` = +7 days.
- AC-5 `AccessController.decide`: permission check; missing record → already handled; grant assigns the
  role and notifies in the requester's language; decline notifies; the record is deleted either way;
  a failing requester notification doesn't fail the decision.
- AC-6 `UsersController`: list sorted with one button per user; the card shows toggles and Remove only
  for editable users (not root, not the caller); a toggle replaces the permissions with the union of the
  ticked roles (ticked = role's permissions ⊆ user's); unticking the last role asks for confirmation;
  confirm → delete → list; non-admin → not allowed; editing root or self is refused even on a forged
  callback.
- AC-7 Screens: templates/params/buttons of §4.2–4.4; callback data < 64 bytes for a 10-digit id;
  `%s` = params; dynamic values escaped.
- AC-8 `UiAction` round-trips for the eight new types; `UiRouter` dispatches them.
- AC-9 `NodeAccess.isOwner` matches id, and username during the transition; `runNode` tags `RunBy` =
  id and `RunByName` = username; `getTask`/`listTasks` map `RunByName`.
- AC-10 Text commands: `/assignRoles @unknown ROLE` creates a pending record; known users are updated
  under their id; `/listRegisteredUsers` marks pending users; ids are accepted.
- AC-11 Messages: all keys of §4.7 in both files, ASCII only.
- AC-12 `java-tests.yml` runs on a PR touching `src/**` (checked by opening the PR); Dockerfile builds
  and `python3.11 -m node_agent` starts (checked by the Docker deploy); node tests run on 3.11 in CI.

## 9. Definition of done

`mvn -B verify`, the Python suite (3.11) and the shell tests pass; every AC has a test; the lazy
migration has been observed in prod (the root's record re-keyed, the old one gone); no TODOs.

## 10. Tasks and file ownership

| Task | Files |
|---|---|
| H1 Housekeeping (tech lead) | `.github/workflows/java-tests.yml`, `node-agent-tests.yml`, `docker/Dockerfile`, `docker/requirements.txt`, `CLAUDE.md` |
| T1 Identity (Java) | `auth/**`, `dynamodb/UserRecord`, `ui/UiContext`, `TgRequestContext`, `dispatcher/**` (commands), `tailscale/**`, `ecs/**`, `ui/controller/NodeAccess`, `HomeController`, `LaunchController`, `NodeController`, `application.yml` |
| T2 Identity tests | `src/test/java/**` for T1 |
| T3 Access + users (Java) | `dynamodb/AccessRequestRecord`, `ConfigTables`, `auth/*AccessRequest*`, `ui/UiAction`, `ui/UiRouter`, `ui/controller/AccessController`, `UsersController`, `ui/screen/AccessScreens`, `UsersScreen`, `HomeScreen`, `HomeModel`, `UiConfigurer`, messages |
| T4 Access + users tests | `src/test/java/**` for T3 |
| Docs (tech lead) | this spec's decision log, roadmap, CLAUDE.md |

Order: H1 → T1/T2 (deploy, observe the migration) → T3/T4 (deploy).

## 11. Deploy checklist (owner)

1. H1: merge-free from the branch: "Deploy Tailscale ECS Resources" (Docker on) per region; open a
   throwaway PR to see `java-tests.yml` run; delete the four variables.
2. T1: "VPN Bot Lambda CI-CD"; open the bot as root → the `USER` record is now keyed by your id with
   `username` set; every other user's record migrates at their next request. Nodes started before
   the deploy still show as yours (username match) until they stop.
3. T3: "VPN Bot Lambda CI-CD"; test with a second Telegram account: request → admin message →
   grant → requester notified → home shows regions; Users screen: list, delete, cancel.

## 12. Decisions to confirm (review)

- D-1 Keying by user id keeps pre-registration by username: such a record stays username-keyed
  ("pending") until the user's first contact, then it is re-keyed (same mechanism as the migration of
  today's records). The text commands' behaviour is unchanged.
- D-2 Lazy migration of username-keyed records on first contact instead of a one-off script
  (the ids are unknown until the users contact the bot).
- D-3 Transition rule for running nodes (`RunBy` = username) – owner match by username too; remove
  the fallback in a later phase.
- D-4 Admins are notified at chat id = their user id (private chats); no `ADMIN_CHAT_ID` setting.
  An admin who never started the bot (impossible for root) would not be reachable – logged.
- D-5 Roles offered on the admin buttons: VPN user (`NODE_ADMIN`) and Read-only; `USER_ADMIN` only
  via `/assignRoles`.
- D-6 Access requests expire after 7 days (TTL); a second tap while pending is refused.
- D-7 Roles are edited from the user card with toggles (union of the ticked roles) instead of a
  separate "change role" flow; `/assignRoles` stays for scripting.
- D-9 The username-takeover window that id-keying closes remains open for pending users until their
  first contact – the same exposure as today, limited to that window.
- D-8 A user whose roles change from the Users screen is not notified (only access requests are);
  can be added later if wanted.

## 13. Decision log

(empty until implementation)
