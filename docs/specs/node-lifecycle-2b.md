# Spec: Phase 2b – Stop and reuse

Status: draft for review · Branch: `feature/node-lifecycle-2b` (based on `feature/node-lifecycle-2a`)
· Roadmap: `docs/roadmap.md`

## 1. Goal

1. Users can stop a node with a 🛑 button – their own nodes in one tap; root can stop anyone's node
   after a confirmation, and the owner is notified.
2. Tapping a region where the user already runs a node offers
   `[📋 Use <name>] [🚀 Start another] [🏠 Menu]` instead of silently starting a second node.
   Starting several nodes in a region stays allowed.

## 2. Non-goals

- No change to the idle auto-stop, to `/runNodeIn` (no reuse check, no stop command), to admin
  commands, metrics, caches or infrastructure (no new IAM: `ecs:StopTask`/`ecs:DescribeTasks` are
  already allowed).
- No "still running" reminder, no maximum lifetime, nothing from Phase 3.
- No new dependencies. No refactoring beyond what this spec lists.

## 3. Ground rules

Same as `docs/specs/node-lifecycle-2a.md` §3 (CLAUDE.md, existing patterns, exact contracts,
file ownership, `SPEC DEVIATION: <what> / <why> / <proposed option>` escalation).

## 4. Behaviour

### 4.1 Node identity in callbacks

A node is referenced as `NodeRef(regionId, taskId)`, encoded `"<regionId>:<taskId>"` (ECS task id =
last segment of the task ARN, 32 hex chars). New `UiAction` types:

| Type | Arg | Meaning |
|---|---|---|
| `RUN_NEW` | regionId | start a node without the reuse check ("Start another") |
| `USE` | NodeRef | show the card of an existing node |
| `STOP` | NodeRef | stop request |
| `STOP_CONFIRM` | NodeRef | confirmed stop (root stopping someone else's node) |

`STOP_CONFIRM:ap-southeast-7:<32 hex>` is 60 bytes – within Telegram's 64-byte limit; `encode()`
keeps enforcing the limit. `NodeRef.parse` accepts any non-blank region and task id without `:`
(the task id may be the `{{TASK_ID}}` placeholder, see §4.5).

### 4.2 Who may stop what (`NodeAccess`)

For user `u` and node `n` (`n.runBy()` = the task's `RunBy` tag):
- `u` has `ROOT_ACCESS` → may stop; needs confirmation unless `n.runBy() == u`.
- `u` has `RUN_NODES` and `n.runBy() == u` → may stop, no confirmation.
- otherwise → may not stop.
The check always uses the node fetched from ECS (`getNode`), never data from the callback.

### 4.3 Stop flow (`NodeController`)

`stop(context, messageId, nodeRef)`:
1. `node = nodeService.getNode(regionId, taskId)`; empty (not found or already stopping/stopped)
   → edit `NodeScreens.alreadyStopped()`; stop.
2. Not allowed (§4.2) → edit `NodeScreens.notAllowed()`; stop.
3. Needs confirmation → edit `NodeScreens.confirmStop(node)`; stop.
4. `nodeService.stopNode(regionId, taskId, "Stopped by @<username> via the bot")`, then edit
   `NodeScreens.stopping(node)`.

`confirmStop(context, messageId, nodeRef)`: steps 1–2, then step 4 without the confirmation check,
then notify the owner (§4.4).

The container receives SIGTERM and edits its own card to the stopped card (2a behaviour).

### 4.4 Owner notification

When root stops someone else's node, the Lambda sends the owner
`NodeScreens.stoppedByAdmin(node)` as a new message, in the owner's language, to the owner's chat.
Chat id and language come from new task tags `ChatId` and `Lang` set at `RunTask` (§4.6). Tasks
without a `ChatId` tag (started before 2b) → no notification (logged).

### 4.5 Stop button on the node card

- `LaunchScreens.ready(hostName, regionId, publicIp, taskId)` gets a last row
  `[🛑 ${ui.button.stop} → STOP:<regionId>:<taskId>] [MENU]` (replacing the plain `[MENU]` row).
- `NodeNotifications` passes `TASK_ID_PLACEHOLDER = "{{TASK_ID}}"` as `taskId`; the node agent
  replaces `{{TASK_ID}}` in `TG_READY_MARKUP` with its own ECS task id (§5). If the agent cannot
  determine it, it removes the buttons whose `callback_data` still contains `{{TASK_ID}}`.
- `USE` shows the same card rendered by the Lambda with real values (host, IP, task id).

### 4.6 Tags and node data

- `RunTask` adds tags `ChatId` (owner chat id) and `Lang` (owner language code) next to `RunBy`;
  tag names come from `application.yml` (`ecs.chat-id-tag: ChatId`, `ecs.language-tag: Lang`).
- `TaskInfo` gets `runBy`, `chatId`, `languageCode` (from tags; null when absent).
- `TailscaleNodeService.runNode(regionId, owner, hostName, environment)` with
  `NodeOwner(username, chatId, languageCode)` replaces the `userTgId` parameter
  (`LaunchController` builds it from `UiContext`).
- `getNode(regionId, taskId)` → `Optional<TaskInfo>`: `DescribeTasks` (with tags) in that region's
  cluster; empty when not found or when the task's desired or last status is `STOPPED`.
- `stopNode(regionId, taskId, reason)` → `StopTask`.

### 4.7 Reuse choice (`LaunchController`)

`launch(context, messageId, regionId)` (RUN):
1. Not allowed → `notAllowed()` (unchanged). 2. Edit `starting(regionId)` (unchanged).
3. Region not supported → `regionUnavailable()` (unchanged).
4. **New:** `existing = nodeService.listTasks(username)` filtered to `regionId`; non-empty → edit
   `LaunchScreens.existingNodes(regionId, existing)`; stop.
5. `startNode(...)` (unchanged).

`launchAnother(context, messageId, regionId)` (RUN_NEW): steps 1–3 and 5 (no reuse check).

`NodeController.use(context, messageId, nodeRef)`: `getNode`; empty → `alreadyStopped()`; node not
owned by the user and user not root → `notAllowed()`; else edit `ready(host, regionId, ip, taskId)`.

### 4.8 Home screen

`HomeModel` gets `List<String> stoppableTaskIds` (computed by `HomeController` with `NodeAccess`).
`HomeScreen` adds, after the region rows and before Refresh/Help, one row per stoppable node in
list order: `[🛑 <hostName> → STOP:<regionId>:<taskId>]`.

### 4.9 Screens

`MENU`, `LINKS` = `CommonButtons`. Dynamic values in button labels have `%` escaped as `%%`
(the translator formats labels).

| Screen | Template | Params | Keyboard |
|---|---|---|---|
| `LaunchScreens.existingNodes(regionId, nodes)` | `"ℹ️ ${ui.reuse.existing}\n📍 %s"` | `[label(regionId)]` | one row per node `[📋 ${ui.button.use} <host> → USE]`, then `[🚀 ${ui.button.start-another} → RUN_NEW:<regionId>]`, then `[MENU]` |
| `NodeScreens.stopping(node)` | `"🛑 <b>%s</b> ${ui.node.stopping}"` | `[host]` | `[MENU]` |
| `NodeScreens.confirmStop(node)` | `"❓ ${ui.stop.confirm} <b>%s</b> (%s)?"` | `[host, "@" + runBy]` | `[🛑 ${ui.button.yes-stop} → STOP_CONFIRM]`, `[↩️ ${ui.button.cancel} → HOME]` |
| `NodeScreens.alreadyStopped()` | `"⚪ ${ui.node.already-stopped}"` | `[]` | `[MENU]` |
| `NodeScreens.notAllowed()` | `"⛔ ${ui.stop.not-allowed}"` | `[]` | `[MENU]` |
| `NodeScreens.stoppedByAdmin(node)` | `"🛑 <b>%s</b> ${ui.node.stopped-by-admin}"` | `[host]` | `[🚀 ${ui.button.start-again} → RUN:<regionId>]`, `[MENU]` |

New keys (both files, ASCII `\uXXXX`, no `%`, no HTML):

| Key | EN | RU |
|---|---|---|
| ui.button.stop | Stop | Остановить |
| ui.button.yes-stop | Yes, stop | Да, остановить |
| ui.button.cancel | Cancel | Отмена |
| ui.button.use | Use | Использовать |
| ui.button.start-another | Start another | Запустить ещё один |
| ui.reuse.existing | You already have a VPN server running in this region. | У вас уже запущен VPN-сервер в этом регионе. |
| ui.node.stopping | is stopping. | останавливается. |
| ui.node.already-stopped | This VPN server is not running anymore. | Этот VPN-сервер уже не запущен. |
| ui.node.stopped-by-admin | was stopped by an administrator. | остановлен администратором. |
| ui.stop.confirm | Stop | Остановить |
| ui.stop.not-allowed | You are not allowed to stop this VPN server. | У вас нет прав на остановку этого VPN-сервера. |

### 4.10 Routing

`UiRouter.dispatch`: `RUN_NEW` → `launchController.launchAnother`; `USE` → `nodeController.use`;
`STOP` → `nodeController.stop`; `STOP_CONFIRM` → `nodeController.confirmStop`.

## 5. Node agent (Python)

- `fetch_task_id(env, session=None) -> Optional[str]`: GET `$ECS_CONTAINER_METADATA_URI_V4/task`
  (5 s timeout), returns the last `/` segment of `TaskARN`; any error or missing variable → `None`.
- `Notifier.ready(public_ip, task_id=None)`: in `TG_READY_MARKUP`, replaces `{{TASK_ID}}` with
  `task_id`; when `task_id` is `None`, removes every button whose `callback_data` contains
  `{{TASK_ID}}` and drops rows that become empty (no markup if nothing is left).
- `agent.run` calls `fetch_task_id(env)` after Tailscale is up and passes it to `ready`.

## 6. Contracts

```java
// org.github.akarkin1.ui
public record NodeRef(String regionId, String taskId) {   // validates non-blank, no ':'
  public String encode();                                  // "<regionId>:<taskId>"
  public static Optional<NodeRef> parse(String value);
}
UiAction.Type: HOME, HELP, RUN, RUN_NEW, USE, STOP, STOP_CONFIRM
UiAction.runNew(String regionId); UiAction.use(NodeRef); UiAction.stop(NodeRef); UiAction.confirmStop(NodeRef)
public Optional<NodeRef> nodeRef();                         // for USE/STOP/STOP_CONFIRM

// org.github.akarkin1.ui.controller
public class NodeAccess {            // (Authorizer)
  public boolean canStop(String username, TaskInfo node);
  public boolean needsConfirmation(String username, TaskInfo node);
  public boolean canView(String username, TaskInfo node);   // owner or root
}
public class NodeController {         // (TailscaleNodeService, NodeAccess, UiMessenger, NodeScreens, LaunchScreens)
  public void use(UiContext context, Integer messageId, NodeRef node);
  public void stop(UiContext context, Integer messageId, NodeRef node);
  public void confirmStop(UiContext context, Integer messageId, NodeRef node);
}
LaunchController.launchAnother(UiContext context, Integer messageId, String regionId)
HomeController(TailscaleNodeService, Authorizer, NodeAccess, UiMessenger, HomeScreen, HelpScreen)
UiRouter(HomeController, LaunchController, NodeController, UiMessenger, ErrorScreen)

// org.github.akarkin1.ui.screen
public class NodeScreens { NodeScreens(RegionLabels); stopping, confirmStop, alreadyStopped, notAllowed, stoppedByAdmin → Screen }
LaunchScreens.ready(String hostName, String regionId, String publicIp, String taskId)
LaunchScreens.existingNodes(String regionId, List<TaskInfo> nodes)
HomeModel(..., List<TaskInfo> nodes, List<String> regionIds, List<String> stoppableTaskIds)
NodeNotifications.TASK_ID_PLACEHOLDER = "{{TASK_ID}}"

// org.github.akarkin1.tailscale / ecs
public record NodeOwner(String username, Long chatId, String languageCode) {}   // tailscale package
TailscaleNodeService.runNode(String regionId, NodeOwner owner, String hostName, Map<String,String> environment) → TaskInfo
TailscaleNodeService.getNode(String regionId, String taskId) → Optional<TaskInfo>
TailscaleNodeService.stopNode(String regionId, String taskId, String reason) → void
EcsManager.getTask(Region region, String taskId) → Optional<TaskInfo>
EcsManager.stopTask(Region region, String taskId, String reason) → void
TaskInfo: + runBy, chatId (String), languageCode
EcsConfiguration: + chatIdTag, languageTag
```

```python
# docker/node_agent/src/node_agent/agent.py
def fetch_task_id(env, session=None) -> Optional[str]
# docker/node_agent/src/node_agent/notifier.py
def ready(self, public_ip, task_id=None) -> None
```

## 7. Acceptance criteria

- AC-1 `NodeRef`/`UiAction`: encode/decode round-trip for all new types; `STOP_CONFIRM` with a
  14-char region and 32-char task id fits in 64 bytes; invalid args rejected.
- AC-2 `NodeAccess` truth table: owner with RUN_NODES / owner without RUN_NODES / other user /
  root on own node / root on other's node / read-only user.
- AC-3 `NodeController.stop`: already stopped; not allowed; owner → stopNode + stopping;
  root on other's node → confirmStop screen and no stopNode.
- AC-4 `NodeController.confirmStop`: re-checks access; stops; notifies the owner in the owner's
  language and chat; no notification without `ChatId` tag; not allowed for non-root.
- AC-5 `NodeController.use`: card with real host/IP/task id; already stopped; not allowed.
- AC-6 `LaunchController.launch` shows `existingNodes` when the user has nodes in that region
  (only that region, only own nodes) and does not call `runNode`; otherwise unchanged 2a flow;
  `launchAnother` skips the check; `runNode` receives the `NodeOwner` from the context.
- AC-7 Screens of §4.9 and the new `ready` row; `%s` = params; labels with `%` are escaped.
- AC-8 Home: stop buttons only for stoppable nodes, in order, between region rows and Refresh/Help.
- AC-9 `EcsManagerImpl`: `startTask` tags include `ChatId`/`Lang`; `getTask` maps tags to
  `runBy`/`chatId`/`languageCode`, empty for stopped/unknown tasks; `stopTask` calls `StopTask`
  with cluster, task and reason.
- AC-10 `UiRouter` dispatches the four new types.
- AC-11 `NodeNotifications`: ready markup contains `STOP:<region>:{{TASK_ID}}`; size < 8192.
- AC-12 Messages: all new keys exist in both files.
- AC-P7 `fetch_task_id`: task id from the metadata response; `None` without the variable, on
  HTTP error or bad JSON.
- AC-P8 `Notifier.ready`: placeholder replaced in the markup; with `task_id=None` the Stop button
  is removed and empty rows dropped; other buttons untouched.

## 8. Definition of done

`mvn -B verify` and the Python suite pass; only files listed in §9 (plus this spec and the roadmap)
changed; every AC has a test; no TODOs or dead code.

## 9. Tasks and file ownership

| Task | Files |
|---|---|
| T1 Foundation | all §6 signatures as compilable stubs; `TaskInfo` fields; `EcsConfiguration` tags + `application.yml`; `NodeOwner`; message keys of §4.9; wiring in `UiConfigurer`/handler so it compiles |
| T2 Java tests | `src/test/java/**` |
| T3 Stop | `ui/NodeRef`, `ui/UiAction`, `ui/UiRouter`, `ui/controller/NodeAccess`, `ui/controller/NodeController`, `ui/controller/HomeController`, `ui/screen/NodeScreens`, `ui/screen/HomeScreen`, `ui/screen/HomeModel` |
| T4 Reuse + ECS | `ui/controller/LaunchController`, `ui/screen/LaunchScreens`, `ui/messenger/NodeNotifications`, `tailscale/**`, `ecs/**` |
| T5 Node agent | `docker/node_agent/src/node_agent/*.py` |
| T6 Python tests | `docker/node_agent/tests/**` |

## 10. Deploy checklist

1. Merge → CI deploys the Lambda code (new tags apply to nodes started afterwards).
2. Run "Deploy Tailscale ECS Resources" (Docker) for each region so the agent fills `{{TASK_ID}}`.
   With an old image the card's Stop button is sent with the placeholder and answers "not running
   anymore" – stopping from the home screen still works.

## 11. Manual test checklist

1. Home lists your node with a 🛑 button → tap → "is stopping" → the node card turns "Stopped"
   and the node disappears from home.
2. Node card 🛑 → same as 1.
3. Root: 🛑 on a friend's node → confirmation → Yes → stops; the friend gets "stopped by an
   administrator" in their language. Cancel → home.
4. Read-only user: no 🛑 buttons.
5. Tap a region where you already run a node → choice; "Use" → card; "Start another" → a second
   node starts.
6. Double tap on a region → the second tap shows the choice instead of starting a second node
   (unless both taps land within the same second).

## 12. Decision log

- (empty)
