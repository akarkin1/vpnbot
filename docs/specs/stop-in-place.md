# Spec: Stop updates in place

Status: approved for implementation · Branch: `feature/stop-in-place` (based on `feature/node-lifecycle-2b`)
· Roadmap: `docs/roadmap.md`

## 1. Goal

User feedback on 2b: "the stopped update should come in the same message, just like the start –
otherwise the container sends a new message, the menu message moves up and you have to tap Menu
again". After this change a stopped node leaves no new messages in the chat:

1. Idle stop and manual stop both **edit the node's message** (`TG_MESSAGE_ID`, the message the
   node was started from) into a stopped card with `[🚀 Start again] [🏠 Menu]`.
   No separate "stopped" message any more.
2. The silent "stops in 2 minutes" warning stays a separate message (an edit would not be noticed),
   but the agent **deletes it** when the node stops or when a device connects again.

## 2. Non-goals

- No change to the idle timeout, warning time, the Lambda's stop flow (`NodeController`), the
  root's owner notification (`stoppedByAdmin` stays a new message – it goes to another person),
  `/runNodeIn`, metrics, infrastructure or IAM. No new dependencies.
- Known limitation, accepted: `TG_MESSAGE_ID` may show another screen by the time the node stops
  (the user tapped 🏠 Menu on the card and navigated on). The stopped card then replaces that screen;
  its 🏠 Menu button brings the user back. (Today's "card marked stopped" edit has the same
  behaviour, but without buttons.)

## 3. Ground rules

Same as `docs/specs/node-lifecycle-2a.md` §3 (CLAUDE.md, existing patterns, exact contracts,
file ownership, `SPEC DEVIATION: <what> / <why> / <proposed option>` escalation).

## 4. Behaviour

### 4.1 Screens (`LaunchScreens`)

Both stopped screens become cards with the same keyboard, one row:
`[🚀 ${ui.button.start-again} → UiAction.run(regionId)] [MENU]`.

| Screen | Template | Params |
|---|---|---|
| `stopped(hostName, regionId)` (idle stop) | `⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped-idle}` | `orDash(hostName)`, `label(regionId)` |
| `stoppedCard(hostName, regionId)` (stopped by a 🛑 tap / SIGTERM) | `⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}` (unchanged) | unchanged |

Message texts (the key now starts a sentence):

| Key | EN | RU |
|---|---|---|
| `ui.node.stopped-idle` | `Stopped: no devices were connected for 10 minutes.` | `Остановлен: 10 минут без подключённых устройств.` |

(RU written as `\uXXXX` escapes, as in the rest of the file.)

### 4.2 Environment (`NodeNotifications`)

Unchanged code: `put(...)` already emits `<PREFIX>_MARKUP` when a screen has buttons, so the task
environment gains `TG_STOPPED_CARD_MARKUP`. All 2b variable names stay, so an old agent started by
the new Lambda and a new agent started by the old Lambda both keep working:

| Variable | Old agent | New agent |
|---|---|---|
| `TG_STOPPED_TEXT` + `TG_STOPPED_MARKUP` | sent as a new message on idle stop | edit of `TG_MESSAGE_ID` on idle stop |
| `TG_STOPPED_CARD_TEXT` (+ `TG_STOPPED_CARD_MARKUP`) | edit on stop, no keyboard | edit on manual stop (keyboard if present) |

### 4.3 Node agent

`TelegramClient`:
- `send_message(...)` returns the sent message's `message_id` (`int`, from `result.message_id`)
  or `None` on any failure (instead of `True`/`False`).
- New `delete_message(chat_id, message_id) -> bool` (`deleteMessage`), same error handling and
  token masking as the other calls.

`IdleMonitor`: new `Action.RESUME`, returned by `observe` when there are active peers again after a
`WARN` (once; afterwards `NONE` as before). Idle time and the warned flag reset as today.

`AgentConfig`: new `stopped_card_markup` from `TG_STOPPED_CARD_MARKUP` (optional).

`Notifier`:
- `idle_warning()`: sends the warning as today (silent) and remembers the returned message id.
- `activity_resumed()` (new): deletes the remembered warning (if any) and forgets it.
- `stopped_idle()`: deletes the warning (if any); then edits `TG_MESSAGE_ID` with
  `TG_STOPPED_TEXT` and `TG_STOPPED_MARKUP`. Without `TG_STOPPED_TEXT` it falls back to `stopped()`.
  No `send_message` any more.
- `stopped()`: deletes the warning (if any); then edits `TG_MESSAGE_ID` with `TG_STOPPED_CARD_TEXT`
  and `TG_STOPPED_CARD_MARKUP` (no markup when the variable is missing).
- Every Telegram call stays wrapped in `_safely`; a failed delete never prevents the edit.

`agent.run`: on `Action.RESUME` calls `notifier.activity_resumed()`.

## 5. Contracts

```java
// org.github.akarkin1.ui.screen.LaunchScreens – signatures unchanged
public Screen stopped(String hostName, String regionId);
public Screen stoppedCard(String hostName, String regionId);
```

```python
# telegram.py
def send_message(self, chat_id, text, reply_markup=None, silent=False) -> Optional[int]
def delete_message(self, chat_id, message_id) -> bool
# monitor.py
class Action(Enum): NONE, WARN, STOP, RESUME = "resume"
# config.py
stopped_card_markup: Optional[str] = None      # TG_STOPPED_CARD_MARKUP
# notifier.py
def idle_warning(self) -> None
def activity_resumed(self) -> None
def stopped_idle(self) -> None
def stopped(self) -> None
```

## 6. Acceptance criteria

- AC-1 `LaunchScreens.stopped`: template and params of §4.1; keyboard `[start again → RUN:region][MENU]`.
- AC-2 `LaunchScreens.stoppedCard`: same keyboard; template unchanged.
- AC-3 `NodeNotifications.build`: `TG_STOPPED_CARD_MARKUP` present with `RUN:<region>` and the menu
  button; `TG_STOPPED_TEXT` rendered in EN and RU with the new texts; whole environment < 8192 bytes.
- AC-4 Messages: `ui.node.stopped-idle` in both files as in §4.1; files stay ASCII.
- AC-P1 `TelegramClient.send_message` returns `result.message_id`; `None` on HTTP error, `ok:false`,
  exception, or a response without `result.message_id`.
- AC-P2 `TelegramClient.delete_message` posts `deleteMessage` with `chat_id`/`message_id`; `True`
  on success, `False` on failure; token never logged.
- AC-P3 `IdleMonitor`: `RESUME` exactly once after a `WARN` when peers come back; never without a
  prior `WARN`; a new idle period can warn again.
- AC-P4 `Notifier.stopped_idle`: one edit of `TG_MESSAGE_ID` with stopped text + stopped markup,
  no `send_message`; deletes a sent warning first; no delete when no warning was sent or its send
  failed; falls back to the card text without `TG_STOPPED_TEXT`; edit happens even if delete fails.
- AC-P5 `Notifier.stopped`: edit with card text + card markup (none when missing); deletes a sent
  warning.
- AC-P6 `Notifier.activity_resumed`: deletes the warning once; a second call does nothing.
- AC-P7 `agent.run`: `RESUME` → `activity_resumed()`; idle stop → warning deleted and card edited,
  no new message.
- AC-P8 Disabled notifier (no token/chat/message id): no Telegram calls.

## 7. Definition of done

`mvn -B verify` and the Python suite pass; only the files of §8 (plus this spec, the roadmap and
CLAUDE.md) changed; every AC has a test; no TODOs or dead code.

## 8. Tasks and file ownership

| Task | Files |
|---|---|
| T1 Implementation | `ui/screen/LaunchScreens.java`, `messages.properties`, `messages_ru.properties`, `docker/node_agent/src/node_agent/{telegram,monitor,config,notifier,agent}.py` |
| T2 Java tests | `src/test/java/**`, `src/test/resources/**` |
| T3 Python tests | `docker/node_agent/tests/**` |

## 9. Deploy checklist

1. Deploy the Lambda (texts, keyboards) and run "Deploy Tailscale ECS Resources" with Docker for each
   region (agent). Either order works (§4.2); nodes keep the agent and texts they were started with.

## 10. Manual test checklist

1. Start a node, leave it idle: after 8 min a silent warning appears; after 10 min the warning
   disappears and the node card turns into "Stopped: no devices…" with `[🚀 Start again] [🏠 Menu]`.
2. Start a node, leave it idle until the warning, then connect a device: the warning disappears,
   the node keeps running.
3. Tap 🛑 on the card: "is stopping" → the same message becomes "Stopped" with
   `[🚀 Start again] [🏠 Menu]`.
4. 🚀 Start again → the usual launch flow in the same message.

## 11. Decision log

- D-1 (T1) `Notifier._safely` returns the call's result (`None` on an exception), so `idle_warning`
  can keep the warning's message id.
- D-2 (T3) `stopped()` without `TG_STOPPED_CARD_TEXT` still deletes a sent warning (no edit follows);
  the `stopped_idle` → `stopped()` fallback deletes the warning once.
- D-3 (T3) `activity_resumed` forgets the warning even if the delete fails (no retry).
- D-4 (T3) `send_message` returns `None` on a non-200 response even if its body has a message id.
- D-5 (T2) The environment size check (AC-3) counts UTF-8 bytes, not characters.
- D-6 (T2) No tests for a `null` region in the stopped screens: `UiAction.run(null)` is invalid and
  the Lambda always knows the region.
