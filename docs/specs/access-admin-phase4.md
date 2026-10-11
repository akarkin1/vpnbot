# Spec: Phase 4 – Access & admin (+ housekeeping)

Status: approved for implementation (2026-10-11) · Branch: `feature/access-admin` (based on `main`) · Roadmap: `docs/roadmap.md`

## 1. Goal

1. **Identity by Telegram user id.** Permissions and node ownership are keyed by the immutable user id
   instead of the username, so a renamed account keeps its access and a freed username cannot be
   taken over by someone else. Usernames stay for display and for the admin text commands.
2. **Request access.** A user without access taps `🙋 Request access`; every admin gets a message with
   role buttons; the requester is notified of the decision. No more out-of-band "ask @karkin_ai".
3. **Users screen.** Admins manage users from the UI: a list with roles and a 🗑 button per user.
4. **Housekeeping** carried over from Phase 3: Java tests on pull requests, node image on Python 3.11.

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
  id as a decimal string when known, else `@username` – used to reference a user in callback data.
  `UiContext` gains `Long userId` (from `User.getId()`), and `UiContext.user()` returns the `TgUser`.
  `TgRequestContext` (legacy, used by text commands) gains `getUser()` returning the same.
- `Authorizer.hasPermission(TgUser user, Permission permission)` replaces the username variant;
  every caller passes the `TgUser` of the request. The root/"all nodes" checks in `HomeController`,
  `NodeAccess`, `ListNodesCommand` and `CommandDispatcher.checkUserPermissions` use it.
- `PermissionsService`:
  - `Map<TgUser, List<Permission>> getUsers()`: every known user – `USER` records (id + username) and
    `PENDING_USER` records (username only); replaces `getUserPermissions()` (and
    `UserPermissionsProvider`).
  - `updateUserPermissions(TgUser user, Set<Permission> permissions)`: with an id → `USER` record
    (username attribute: the given one, or the stored one when null); without an id → `PENDING_USER`
    record keyed by the username. Null/empty permissions → delete that record.
    `assignRolesToUser`/`deleteUser` defaults take a `TgUser` likewise.
  - `Optional<TgUser> findByUsername(String username)`: case-insensitive, without `@`; a `USER` record
    with that username attribute wins over a `PENDING_USER` record.
- Records (§5): `USER` is always keyed by the user id; `PENDING_USER` is keyed by the username and
  holds the permissions of a user pre-registered by an admin who has not contacted the bot yet.
  **Activation on first contact** (D-2, D-24): `PendingUserActivator.activate(TgUser)`, called once
  per request by the Lambda handler right after the request context is set and before any permission
  check: for a user with no `USER` record it looks for `PENDING_USER` by the request's username; if
  found it writes the `USER` record (same permissions, `username` attribute) and deletes the pending
  one, in that order. `WhiteListAuthorizer.hasPermission` is read-only and answers from the `USER`
  record only. Pre-registration therefore works as today; the record changes type once the user
  shows up.
- One-off migration of today's username-keyed `USER` records (D-2): `scripts/migrate-pending-users.sh
  [--dry-run]` (AWS CLI + `jq`; same style and fake-`aws` test as the Phase 3 script) scans `pk =
  USER`, and for every item whose sk is not all digits writes `PENDING_USER`/`<sk>` with the same
  `permissions` and deletes the `USER` item. Run by the owner before the T1 deploy; idempotent.
- The workflow bootstrap of the root user (`deploy-tgbot-lambda.yml`) writes the `USER` record keyed
  by the new repository variable `TG_ROOT_USER_ID` (username attribute from `TG_ROOT_USERNAME`,
  `ROOT_ACCESS`) under `attribute_not_exists(pk)`, so it never overwrites; without the variable the
  step is skipped with a warning (only a fresh installation needs it). It never writes a pending
  record (D-23).
- Node ownership: the `RunBy` tag holds the user id; new tag `RunByName` (config `run-by-name-tag`)
  holds the username for display (`NodeOrigin` gains `userId`; `EcsNode` gains `runByName`).
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
`🙋 <b>First Last</b> (@username, id) ${ui.access.asks}` (just `First` when the account has no last
name, no `(@…` part without a username) with role **checkboxes** and two actions:
`[☐ ${ui.role.node-admin}] [☐ ${ui.role.read-only}]`, `[☐ ${ui.role.user-admin}]`,
`[✅ ${ui.button.grant}] [✖ ${ui.button.decline}]`.

The selection lives in the callback data, not in the table (D-5): every button of the message carries
the current selection as a bitmask `m` of `UserRole` bits (`RoleSet.encode/decode`,
`NODE_ADMIN = 1, READ_ONLY = 2, USER_ADMIN = 4`):
- role tap → `UiAction.accessToggle(userId, m, role)` (`ACCESS_TOGGLE:<id>:<m>:<ROLE>`) →
  `AccessController.toggle(...)`: permission check (`USER_MANAGEMENT` or root); if the request record is
  gone → `alreadyHandled()`; else redraw the same message with the role flipped (✅/☐) and the new
  mask on every button. Nothing is written.
- `[✅ Grant]` → `UiAction.accessGrant(userId, m)` (`ACCESS_GRANT:<id>:<m>`); with `m = 0` the tap only
  answers the callback (nothing changes); otherwise `AccessController.grant(..., roles)`.
- `[✖ Decline]` → `UiAction.accessDecline(userId)` (`ACCESS_DECLINE:<id>`) → `AccessController.decline(...)`.
Each admin's message keeps its own selection; the first decision wins (see step 2 below).

`AccessController.grant(context, messageId, userId, Set<UserRole> roles)` /
`AccessController.decline(context, messageId, userId)` (D-25):
1. Caller must have `USER_MANAGEMENT` (or root), else edit to `accessScreens.notAllowed()`.
2. Read the request record; if missing → edit to `accessScreens.alreadyHandled()`
   (`${ui.access.already-handled}`), done.
3. Grant: `assignRolesToUser(TgUser(userId, request.username), roles)` (union of the roles'
   permissions); decline: nothing.
4. Delete the request record; edit the admin's message to `accessScreens.granted(request, roles)` /
   `accessScreens.declined(request)` (no buttons); send the requester (chat id = user id, language
   from the record) `accessScreens.accessGranted(roles)` (`${ui.access.granted}` with the role names
   joined by ", " + `[🏠 Menu]`) or `accessScreens.accessDeclined()` (`${ui.access.declined}`, no
   buttons). A failing requester notification is logged, not an error.

### 4.4 Users screen

Home screen: for users with `USER_MANAGEMENT` (or root) a row `[👥 ${ui.button.users}]`
(`UiAction.users()`, `USERS`) between the stop rows and Refresh/Help.

**List** – `UsersController.show(context, messageId)` → `usersScreen.list(model)`: title
`${ui.users.title}`, one button per user, sorted by username then key, labelled
`@username · <roles>` (or `id · <roles>` without a username; a pending user gets the suffix
`· ${ui.users.pending}`; `RoleFormat.describe`: the `UserRole`s whose permission sets are covered,
root → `${ui.role.root}`, otherwise the permission names), each `UiAction.user(key)` (`USER:<key>`,
key = `TgUser.key()`: the id, or `@username` for a pending user); last row `[🏠 Menu]`. Users are
referenced by that key in every callback of this screen (`<key>` below), so pending users can be
edited and removed too.

**User card** – `UsersController.showUser(context, messageId, key)` → `usersScreen.user(row)`:
`👤 <b>@username</b> (id or ${ui.users.pending})\n<roles>` and, unless the target is a root user or the caller:
- role toggles, one row `[✅|☐ ${ui.role.node-admin}] [✅|☐ ${ui.role.read-only}]` and one row
  `[✅|☐ ${ui.role.user-admin}]` (`UiAction.toggleRole(key, role)` → `USER_ROLE:<key>:<ROLE>`,
  `ROLE` ∈ `UserRole`); a role is ticked when its permission set is a subset of the user's permissions;
- `[🗑 ${ui.button.remove}]` (`USER_DEL:<key>`), only for callers with `DELETE_USERS` (or root), as
  `/deleteUsers` requires;
- `[◀ ${ui.button.users}]` (`USERS`).
For the caller and for a root user the card has only the back button (root is a single user by
design and is never edited from the UI).

Toggle → `UsersController.toggleRole(context, messageId, key, role)`: permission check
(`USER_MANAGEMENT`); refuse for the caller and for a root target (card unchanged); compute the ticked
set after the toggle; if it is empty → `usersScreen.confirmDelete(row)` (same as 🗑); else
`assignRolesToUser(row.user(), tickedRoles)` (the union of the roles' permissions, replacing the
previous permissions) and show the card again. The affected user is not notified (D-8).

🗑 → `usersScreen.confirmDelete(row)`: `${ui.users.confirm-delete}` with
`[✅ ${ui.button.confirm-delete}]` (`USER_DEL_CONFIRM:<key>`) `[✖ ${ui.button.cancel}]` (`USER:<key>`).
Confirm → `UsersController.delete(...)`: permission check (`DELETE_USERS` or root), refuse self and
root targets even on a forged callback, `deleteUser(row.user())`, back to the list.

### 4.5 Routing

`UiAction.Type` += `REQUEST_ACCESS, ACCESS_TOGGLE, ACCESS_GRANT, ACCESS_DECLINE, USERS, USER, USER_ROLE, USER_DEL, USER_DEL_CONFIRM`;
`UiAction.userId()` (`Optional<Long>`), `UiAction.roleMask()` (`OptionalInt`) and `UiAction.role()`
(`Optional<UserRole>`) for the typed ones. `UiRouter` dispatches them to `AccessController` / `UsersController`. Callback data stays under
64 bytes (`USER_ROLE:@<32-char username>:NODE_ADMIN` = 54 and `ACCESS_TOGGLE:9999999999:7:USER_ADMIN`
= 37 are the longest).

### 4.6 Text commands (existing, behaviour unchanged)

`/assignRoles`, `/deleteUsers`, `/listRegisteredUsers` keep their syntax and behaviour. Internally a
`@username` argument is resolved with `findByUsername`; an unknown username in `/assignRoles` creates a
`PENDING_USER` record, which is what pre-registration meant until now. `/listRegisteredUsers` prints
`@username (id): roles`, or `@username (pending): roles` for a pending user. A numeric id is accepted too.

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
| `ui.button.grant` | Grant | Выдать доступ |
| `ui.button.decline` | Decline | Отклонить |
| `ui.role.node-admin` | Node admin | Администратор VPN-серверов |
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
| `USER` (changed) | user id | `username` (S, nullable), `permissions` (SS) |
| `PENDING_USER` (new) | username (as typed by the admin, without `@`) | `permissions` (SS) |
| `ACCESS_REQUEST` (new) | user id | `username` (S, nullable), `firstName` (S), `lastName` (S, nullable), `languageCode` (S), `requestedAt` (N, epoch ms), `expiresAt` (N, TTL = requestedAt + 7 days) |

Beans `PendingUserRecord` and `AccessRequestRecord` in `dynamodb` (same style as the others, keys on
Lombok getters); `ConfigTables` gains `pendingUsers()` and `accessRequests()`. Service `auth.DynamoDbAccessRequestService implements
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

## 7. Contracts

```java
// auth
public record TgUser(Long id, String username) { public String key(); boolean pending(); }   // id or username, one may be null; pending = no id
public interface Authorizer { boolean hasPermission(TgUser user, Permission permission); }   // read-only
public class PendingUserActivator { public void activate(TgUser user); }                     // once per request, before any check
public interface PermissionsService extends UserSignupService {
  Map<TgUser, List<Permission>> getUsers();                // USER and PENDING_USER records
  Optional<TgUser> findByUsername(String username);
}
UserSignupService: updateUserPermissions(TgUser, Set<Permission>); deleteUser(TgUser); assignRolesToUser(TgUser, Set<UserRole>)
public record AccessRequest(long userId, String username, String firstName, String lastName, String languageCode, long requestedAt) {}
public interface AccessRequestService { Optional<AccessRequest> find(long userId); void create(AccessRequest r); void delete(long userId); }
public final class RoleFormat { public static String describe(List<Permission> p, Translator t); }   // or Screen-side

// ui
UiContext(Long chatId, Long userId, String username, String firstName, String lastName, String languageCode) + TgUser user()
UiAction: requestAccess(), accessToggle(long userId, int mask, UserRole role), accessGrant(long userId, int mask), accessDecline(long userId), users(), user(String key), toggleRole(String key, UserRole), deleteUser(String key), confirmDeleteUser(String key)
         + Optional<Long> userId(), Optional<String> userKey(), OptionalInt roleMask(), Optional<UserRole> role()
public final class RoleSet { static int encode(Set<UserRole>); static Set<UserRole> decode(int); }   // auth
// ui.controller
public class AccessController { request(UiContext, Integer messageId); toggle(UiContext, Integer messageId, long userId, int mask, UserRole role); grant(UiContext, Integer messageId, long userId, Set<UserRole> roles); decline(UiContext, Integer messageId, long userId); }
public class UsersController  { show(UiContext, Integer messageId); showUser(UiContext, Integer messageId, String key); toggleRole(UiContext, Integer messageId, String key, UserRole role); confirmDelete(UiContext, Integer messageId, String key); delete(UiContext, Integer messageId, String key); }
// ui.screen
public class AccessScreens { requested(); alreadyRequested(); adminRequest(AccessRequest, Set<UserRole> selected); granted(AccessRequest, Set<UserRole>); declined(AccessRequest); alreadyHandled(); accessGranted(Set<UserRole>); accessDeclined(); notAllowed(); }
public class UsersScreen   { list(UsersModel); user(UserRow); confirmDelete(UserRow); notAllowed(); }
public record UsersModel(List<UserRow> users) {}   public record UserRow(TgUser user, List<Permission> permissions, boolean editable) {}   // editable = not root, not the caller; user.pending()
HomeModel: + boolean canManageUsers, boolean hasAccess
// tailscale / ecs
NodeOrigin(long userId, String username, Long chatId, String languageCode); EcsNode + runByName; EcsConfiguration + runByNameTag
```

## 8. Acceptance criteria

- AC-1 `UiContext.fromUpdate` carries the user id for messages and callbacks; `user()` matches.
- AC-2 `WhiteListAuthorizer`: `USER` record → answers from it; a `PENDING_USER` alone or neither →
  false, nothing written; auth disabled → true; root implies every permission.
- AC-2a `PendingUserActivator`: no `USER` record but a `PENDING_USER` for the username → put `USER`,
  delete `PENDING_USER`, in order, once; `USER` record present, no match, or no id/username → nothing
  written; a failed write is logged, the pending record stays, the request goes on.
- AC-3 `DynamoDbPermissionsService`: `getUsers` returns `USER` and `PENDING_USER` records as `TgUser`s
  (pending = no id); `updateUserPermissions` writes `USER` for an id (keeping the stored username when
  the given one is null) and `PENDING_USER` otherwise; `findByUsername` is case-insensitive, strips `@`,
  prefers `USER`; delete on empty permissions.
- AC-3a `migrate-pending-users.sh` (fake-`aws` test): moves only non-numeric `USER` keys to
  `PENDING_USER`, keeps numeric ones, `--dry-run` writes nothing, re-run is a no-op.
- AC-4 `AccessController.request`: the three branches of §4.2; the admin notification goes to each
  admin's user id as chat id, not to non-admins; records the request with `expiresAt` = +7 days.
- AC-5 `AccessController.toggle`: redraws with the flipped role and the new mask on every button,
  writes nothing, permission check, missing record → already handled. `grant`/`decline`: permission check;
  missing record → already handled; grant with the selected roles assigns their union and notifies in
  the requester's language naming the roles; an empty mask on Grant changes nothing; decline
  notifies; the record is deleted on grant/decline; a failing requester notification doesn't fail the
  decision. `RoleSet` round-trips every subset of `UserRole`.
- AC-6 `UsersController`: list sorted with one button per user; the card shows toggles only for
  editable users (not the caller, not root) and Remove only with `DELETE_USERS`/root; a toggle replaces
  the permissions with the union of the ticked roles (ticked = role's permissions ⊆ user's); unticking
  the last role asks for confirmation; confirm → delete → list; non-admin → not allowed; editing self or
  root is refused even on a forged callback.
- AC-7 Screens: templates/params/buttons of §4.2–4.4; callback data < 64 bytes for a 10-digit id;
  `%s` = params; dynamic values escaped.
- AC-8 `UiAction` round-trips for the nine new types; `UiRouter` dispatches them.
- AC-9 `NodeAccess.isOwner` matches id, and username during the transition; `runNode` tags `RunBy` =
  id and `RunByName` = username; `getTask`/`listTasks` map `RunByName`.
- AC-10 Text commands: `/assignRoles @unknown ROLE` creates a `PENDING_USER` record; known users are
  updated under their id; `/listRegisteredUsers` marks pending users; ids are accepted.
- AC-11 Messages: all keys of §4.7 in both files, ASCII only.
- AC-12 `java-tests.yml` runs on a PR touching `src/**` (checked by opening the PR); Dockerfile builds
  and `python3.11 -m node_agent` starts (checked by the Docker deploy); node tests run on 3.11 in CI.

## 9. Definition of done

`mvn -B verify`, the Python suite (3.11) and the shell tests pass; every AC has a test; the activation
has been observed in prod (the root's `USER` record created, the pending one gone); no TODOs.

## 10. Tasks and file ownership

| Task | Files |
|---|---|
| H1 Housekeeping (tech lead) | `.github/workflows/java-tests.yml`, `node-agent-tests.yml`, `docker/Dockerfile`, `docker/requirements.txt`, `CLAUDE.md` |
| H2 Migration script (tech lead) | `scripts/migrate-pending-users.sh`, `scripts/migrate-pending-users-test.sh`, `deploy-tgbot-lambda.yml` (root bootstrap → `PENDING_USER`) |
| T1 Identity (Java) | `auth/**`, `dynamodb/UserRecord`, `dynamodb/PendingUserRecord`, `ConfigTables`, `ui/UiContext`, `TgRequestContext`, `dispatcher/**` (commands), `tailscale/**`, `ecs/**`, `ui/controller/NodeAccess`, `HomeController`, `LaunchController`, `NodeController`, `application.yml` |
| T2 Identity tests | `src/test/java/**` for T1 |
| T3 Access + users (Java) | `dynamodb/AccessRequestRecord`, `ConfigTables`, `auth/*AccessRequest*`, `ui/UiAction`, `ui/UiRouter`, `ui/controller/AccessController`, `UsersController`, `ui/screen/AccessScreens`, `UsersScreen`, `HomeScreen`, `HomeModel`, `UiConfigurer`, messages |
| T4 Access + users tests | `src/test/java/**` for T3 |
| Docs (tech lead) | this spec's decision log, roadmap, CLAUDE.md |

Order: H1 → T1/T2 (deploy, observe the migration) → T3/T4 (deploy).

## 11. Deploy checklist (owner)

1. H1: merge-free from the branch: "Deploy Tailscale ECS Resources" (Docker on) per region; open a
   throwaway PR to see `java-tests.yml` run.
2. T1: run `scripts/migrate-pending-users.sh --dry-run`, then for real (today's records become
   `PENDING_USER`); then "VPN Bot Lambda CI-CD"; open the bot as root → a `USER` record keyed by your
   id with `username` set appears and your pending record is gone; every other user is activated at
   their next request. Nodes started before the deploy are not listed on the home screen until they
   stop (D-12) but can still be stopped from their card.
3. T3: "VPN Bot Lambda CI-CD"; test with a second Telegram account: request → admin message →
   grant → requester notified → home shows regions; Users screen: list, delete, cancel.

## 12. Decisions (review, all confirmed 2026-10-11)

- D-1 `USER` is always keyed by the user id; pre-registration by username keeps working through a
  separate `PENDING_USER` record (the Bot API has no username → id lookup, so the username is the only
  identifier an admin can give). The text commands' behaviour is unchanged.
- D-2 A pending record becomes a `USER` record at the user's first contact (activation); today's
  username-keyed `USER` records are moved to `PENDING_USER` once by `scripts/migrate-pending-users.sh`
  before the T1 deploy, so no "is this key a username" heuristic is needed in the code.
- D-3 Transition rule for running nodes (`RunBy` = username) – owner match by username too; remove
  the fallback in a later phase.
- D-4 Admins are notified at chat id = their user id (private chats); no `ADMIN_CHAT_ID` setting.
  An admin who never started the bot (impossible for root) would not be reachable – logged.
- D-5 The admin's request message offers the bot's three roles as checkboxes plus Grant/Decline, so
  several roles can be granted at once; the selection is carried in the callback data (a bitmask), so
  toggling writes nothing and each admin's message has its own selection.
- D-6 Access requests expire after 7 days (TTL); a second tap while pending is refused.
- D-7 Roles are edited from the user card with toggles (union of the ticked roles) instead of a
  separate "change role" flow; `/assignRoles` stays for scripting.
- D-9 The username-takeover window that id-keying closes remains open for pending users until their
  first contact – the same exposure as today, limited to that window.
- D-10 Root is out of scope: a single user by design, never granted or edited from the UI.
- D-11 Remove on the user card requires `DELETE_USERS` like `/deleteUsers`; no `UserRole` grants it,
  so removing users stays root-only (decided: keep as is).
- D-8 A user whose roles change from the Users screen is not notified (only access requests are);
  can be added later if wanted.

## 13. Decision log

(empty until implementation)

- D-12 (T1) `listTasks(id)` matches the `RunBy` tag exactly, so nodes started before the deploy (tag =
  username) are not listed for their owner on the home screen until they stop (at most the idle
  timeout); they can still be opened and stopped from their card (ownership matches the username,
  D-3), and root sees them. §11 step 2 is corrected accordingly.
- D-13 (T1) `findByUsername` is a default method of `PermissionsService` over `getUsers()`, so the
  caching decorator answers it from its cache; `getUsers()` makes two queries (USER, PENDING_USER).
- D-14 (T1) `TgUser` equality: by id when present, else by username ignoring case; a user with an id
  never equals one without. A `USER` record with a non-numeric key is skipped with a warning (no
  heuristic, D-2): run `scripts/migrate-pending-users.sh` before the T1 deploy or those users have no
  access until it runs.
- D-15 (T1, changed by D-24) A failed activation write is logged and retried on the user's next
  request; until then the user has no access (the authorizer no longer falls back to the pending
  record).
- D-16 (T2) Activation finds the `PENDING_USER` record case-insensitively (like `findByUsername`).
- D-17 (T2) A user without a username is authorised and lists nodes by id (the old code stopped on a
  null username); the `RunByName` tag is omitted when there is no username; the generated host name
  is still built from the username (falls back to the id).
- D-18 (T2) `/listRegisteredUsers` keeps printing permission names (role names are a Users-screen
  concern); the pending marker is asserted loosely.
- D-19 (T3) No `%s` in `ui.*` values (CLAUDE.md): `ui.access.granted-admin` = "granted:" and
  `ui.access.granted` = "Access granted:" with the role names in the template and a new key
  `ui.access.welcome` ("Welcome!"); `ui.users.confirm-delete` = "Remove the user" + `<b>%s</b>?` in the
  template. Role names are `${ui.role.*}` keys placed in the template (params are not translated).
- D-20 (T3) `UsersScreen.user(row, canRemove)`: the Remove button depends on the caller, not on the row;
  a caller without `DELETE_USERS` who unticks the last role gets the card back unchanged instead of a
  confirmation they could not complete.
- D-21 (T3) `RoleFormat` is screen-side (`describe(Collection<Permission>)` returns template text);
  `RoleSet` holds the role → permissions mapping, which `UserSignupService` now reads from it
  (behaviour unchanged). `DynamoDbAccessRequestService.find` ignores expired records (TTL deletion
  lags). The no-access home keyboard is `[🙋 Request access]` followed by the usual Refresh/Help row.
  Admins are notified with the bot's default language (their record stores none).
- D-22 (T4, superseded by D-26 for the name) The admin request message showed the first name only
  (`AccessRequest` held no last name), without "@null" when there is no username. A node admin shows Read-only as ticked too
  (its permissions are a subset), and unticking Node admin leaves Read-only. Users are listed sorted by
  username case-insensitively, users without a username last. `grant` with an empty role set is a
  no-op (in addition to the router skipping mask 0).
- D-23 (review) The root bootstrap is keyed by `TG_ROOT_USER_ID`, not by a pending username record:
  the "not registered yet" check on a pending record would pass again after the root's activation, so
  every later deploy would recreate `PENDING_USER/<root name>` with `ROOT_ACCESS` – a stale duplicate
  and a takeover risk if the root ever changed username.
- D-24 (review) Activation is not a side effect of `Authorizer.hasPermission`: that method is called
  from every controller and command and its name promises a read-only check, so a DynamoDB write
  behind it was hidden. `PendingUserActivator.activate(TgUser)` does the activation explicitly, once
  per request, from the Lambda handler before routing; `WhiteListAuthorizer` only reads the `USER`
  record. The handler is static wiring without a test, so the call order is covered by review only.
- D-25 (review) `AccessController.decide(..., Optional<Set<UserRole>> roles)` is split into
  `grant(..., Set<UserRole> roles)` and `decline(...)`: an `Optional` parameter is an anti-pattern
  (callers wrap values, the method branches on presence). Both share the permission and
  missing-record checks; behaviour is unchanged.
- D-26 (review) The requester's last name is carried too: §4.3 always showed `First Last`, but §5/§7
  (written from the Phase 2b `UiContext`, which held the first name only) had no last name, and T4
  followed the data model (D-22). `UiContext`, `AccessRequest` and the `ACCESS_REQUEST` record gain a
  nullable `lastName`; the admin message shows `First Last`, or `First` alone when Telegram has no
  last name. Requests stored before this change have no `lastName` attribute and render as before.
