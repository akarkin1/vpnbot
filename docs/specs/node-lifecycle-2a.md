# Spec: Phase 2a – node reports itself, speed, metrics

Status: approved for implementation · Branch: `feature/node-lifecycle-2a` · Roadmap: `docs/roadmap.md`

## 1. Goal

Starting a node no longer keeps the Lambda busy for minutes: the Lambda starts the ECS task and
returns; the container (a new Python "node agent") edits the progress message into the node card
when Tailscale is up, warns before an idle shutdown and reports the shutdown. Every request emits
one metrics line (switchable). The Lambda gets faster (parallel region queries, TTL caches, longer
API Gateway timeout) and reads the bot token from Secrets Manager.

## 2. Non-goals

- No Stop button, no "use existing node" choice (Phase 2b). No DynamoDB, no VPC/NAT/EFS changes,
  no SnapStart (Phase 3). No maximum node lifetime, no "still running" reminder.
- No change to home/help screens, routing, auth, admin commands, deduplication.
- No new Java dependencies. Python dependencies: only `boto3` and `requests` (pinned).
- No refactoring outside what this spec lists.

## 3. Ground rules

- Follow `CLAUDE.md` and imitate existing code (constructor injection, `@RequiredArgsConstructor`,
  `@Log4j2`, `*Configurer` wiring, tests like `HelpCommandV2Test`). Java 21.
- Implement exactly the contracts in §7. Private helpers are fine.
- Only touch the files your task owns (§10).
- **Escalation:** if something cannot be implemented as written, stop and report
  `SPEC DEVIATION: <what> / <why> / <proposed option>`.

## 4. Lambda behaviour

### 4.1 Launch flow (`LaunchController`, implements `NodeLauncher`)

`launch(context, messageId, regionId)` (region button):
1. Username null or no `RUN_NODES` → edit `notAllowed()`; stop.
2. Edit `starting(regionId)`.
3. `regionId` not in `nodeService.getSupportedRegionIds()` → edit `regionUnavailable()`; stop.
4. `startNode(context, messageId, regionId, null)`.

`launchInNewMessage(context, regionId, hostName)` (`/runNodeIn`):
1. `messageId = messenger.send(context, starting(regionId))`.
2. Same as steps 3–4 above (with the given `hostName`).

`startNode(context, messageId, regionId, hostName)` (private):
1. `env = nodeNotifications.build(context, messageId, regionId)`.
2. `nodeService.runNode(regionId, context.username(), hostName, env)`; a `RuntimeException` is
   logged and answered with edit `failed(regionId)`; stop.
3. Edit `waiting(regionId)`. Done – the Lambda does not poll the task.

### 4.2 `/runNodeIn` (`RunNodeCommand`)

Keeps today's argument validation and its messages (missing region, invalid region, unsupported
region, hostname in use). Then calls
`nodeLauncher.launchInNewMessage(context, nodeService.toRegionId(userRegion), userHost)` with
`context = new UiContext(TgRequestContext.getChatId(), TgRequestContext.getUsername(), null,
TgRequestContext.getLanguageCode())`, and returns `EmptyResponse.NONE`. All old progress messages
(`command.run-node.node.running`, `.task.started`, `.status.check-failed`, `.node.start-failed`,
`.node.start-succeed`) and any message key no longer referenced anywhere are removed from both
properties files.

### 4.3 Screens (`LaunchScreens`)

`starting`, `failed`, `regionUnavailable`, `notAllowed` unchanged. `stillStarting` removed.
`MENU`, `LINKS` = `CommonButtons`. Host/IP/label params use `NodeFormat.orDash`.

| Method | Template | Params | Keyboard |
|---|---|---|---|
| `waiting(regionId)` | unchanged | unchanged | `[MENU]` (new) |
| `ready(hostName, regionId, publicIp)` | `"%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}"` | `["🟢", host, label(regionId), ip]` | `LINKS`, `[MENU]` |
| `idleWarning(hostName)` | `"⚠️ <b>%s</b> ${ui.node.idle-warning}"` | `[host]` | none |
| `stopped(hostName, regionId)` | `"🛑 <b>%s</b> ${ui.node.stopped-idle}"` | `[host]` | `[Button.action("🚀 ${ui.button.start-again}", UiAction.run(regionId)), MENU]` |
| `stoppedCard(hostName, regionId)` | `"⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}"` | `[host, label(regionId)]` | none |

New keys (both files, ASCII `\uXXXX` escapes for non-ASCII, no `%`):

| Key | EN | RU |
|---|---|---|
| ui.node.idle-warning | will stop in 2 minutes: no devices are connected. | остановится через 2 минуты: нет подключённых устройств. |
| ui.node.stopped-idle | was stopped after 10 minutes with no devices connected. | остановлен: 10 минут без подключённых устройств. |
| ui.node.stopped | Stopped | Остановлен |
| ui.button.start-again | Start again | Запустить снова |

Removed key: `ui.launch.still-starting`.

### 4.4 Rendering and node notifications

- `ScreenRenderer.render(context, screen)` → `RenderedMessage(text, keyboard)`: exactly what
  `TelegramUiMessenger` does today (translate template with HTML-escaped params; translate button
  labels; `keyboard` is `null` when the screen has no rows). `TelegramUiMessenger` uses it.
- `NodeNotifications.build(context, messageId, regionId)` returns these env vars
  (placeholders `NodeNotifications.HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}"`,
  `PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}"` are passed as screen params):

| Env var | Value |
|---|---|
| `TG_CHAT_ID` | `context.chatId()` |
| `TG_MESSAGE_ID` | `messageId` |
| `TG_READY_TEXT` / `TG_READY_MARKUP` | `ready(HOSTNAME, regionId, PUBLIC_IP)` text / keyboard JSON |
| `TG_IDLE_WARNING_TEXT` | `idleWarning(HOSTNAME)` text |
| `TG_STOPPED_TEXT` / `TG_STOPPED_MARKUP` | `stopped(HOSTNAME, regionId)` text / keyboard JSON |
| `TG_STOPPED_CARD_TEXT` | `stoppedCard(HOSTNAME, regionId)` text |

  Keyboard JSON = Jackson serialization of the telegrambots `InlineKeyboardMarkup`
  (`{"inline_keyboard":[[...]]}`); a `*_MARKUP` var is omitted when the keyboard is null.
  The total size of all keys + values must stay below 8192 characters (ECS override limit).

### 4.5 Metrics (`org.github.akarkin1.metrics`)

- `RequestMetrics` is created once in the handler and passed through constructors/configurers.
- Handler: `metrics.start(updateKind)` once the update is parsed (`"Callback"` for callback
  queries, `"Command"` for a message whose text starts with `/` and is not handled by the UI
  router, otherwise `"Message"`), `metrics.finish()` in a `finally` block.
- Timed components (accumulated per request, thread-safe): `S3` around
  `S3ConfigManager.downloadConfigFromS3`; `ECS` around the whole `EcsManagerImpl.listTasks` and
  around `startTask`; `TELEGRAM` around every `AbsSender.execute` in `TelegramUiMessenger` and
  `BotCommunicator`. Nested timings may overlap (documented, accepted).
- `finish()` writes **one** line to stdout (not through log4j), EMF format:
  `{"_aws":{"Timestamp":<epoch ms>,"CloudWatchMetrics":[{"Namespace":"vpnbot","Dimensions":[["UpdateKind"]],"Metrics":[{"Name":"TotalMs","Unit":"Milliseconds"},{"Name":"S3Ms","Unit":"Milliseconds"},{"Name":"EcsMs","Unit":"Milliseconds"},{"Name":"TelegramMs","Unit":"Milliseconds"}]}]},"UpdateKind":"<kind>","TotalMs":n,"S3Ms":n,"EcsMs":n,"TelegramMs":n}`
- `METRICS_ENABLED` (env, default `true`; anything other than `true`, case-insensitive, = off):
  when off, `time(...)` just runs the action and `finish()` prints nothing.
- `finish()` without `start()` prints nothing.

### 4.6 Speed and secrets

- `EcsManagerImpl.listTasks` queries all supported regions in parallel on an injected
  `ExecutorService` (fixed pool of 8 daemon threads, created in the configurer); results keep the
  order of the supported regions; an exception in any region propagates (as today).
- `CachedS3TaskConfigService(delegate, ttl, clock)`: caches the supported-region list and each
  region's runtime parameters for `ttl`; `ttl` zero = no caching. The `cache-supported-regions` /
  `cache-task-runtime-parameters` flags are removed from `application.yml` and `S3Configuration`.
  `ttl` = `ConfigManager.getConfigCacheTtl()` (env `CONFIG_CACHE_TTL_SEC`, default `300`).
- `SecretManagerRequestAuthenticator` caches the webhook secret value for the same TTL (injected
  `Duration` + `Clock`).
- Bot token: `BotTokenResolver.resolve(secretId, fallbackToken)` returns the secret string when
  `secretId` is not blank (Secrets Manager `GetSecretValue`), otherwise `fallbackToken`. The handler
  calls it once at start-up with `ConfigManager.getBotTokenSecretId()` (env `BOT_TOKEN_SECRET_ID`)
  and `ConfigManager.getBotToken()`.

### 4.7 Removed code

`TailscaleNodeService.checkNodeStatus/getFullTaskInfo`, `EcsManager.checkTaskHealth/getFullTaskInfo`
(+ implementations), `EcsContainerHealth` and `ecs.health` config, `LaunchScreens.stillStarting`,
and anything only they used. `RunTaskStatus` stays (used by `listTasks`).

## 5. Node agent (Python, `docker/node_agent`)

Python 3.9 compatible, standard library + `boto3` + `requests`. Logging via `logging` to stdout.

Start-up (`agent.run`):
1. `config = AgentConfig.from_env(os.environ)`.
2. Tailscale auth key = `get_secret(TAILSCALE_TOKEN_SECRET_ID, TAILSCALE_TOKEN_SECRET_REGION)`;
   failure → log error, exit code 1.
3. Telegram: if `TG_BOT_TOKEN_SECRET_ID` is set, read the token the same way (region
   `TG_BOT_TOKEN_SECRET_REGION`); failure → log warning, notifications disabled. No token, no
   `TG_CHAT_ID` or no `TG_MESSAGE_ID` → notifications disabled (node works as before).
4. Start `tailscaled --tun=userspace-networking --no-logs-no-support` (background process).
5. `tailscale up --authkey=<key> --hostname=<TAILSCALE_HOSTNAME> --advertise-exit-node`, retried
   every 1 s up to 120 attempts; still failing → exit code 1.
6. `notifier.ready(public_ip)` where `public_ip` = body of `https://checkip.amazonaws.com`
   (stripped, 5 s timeout; failure → `—`).
7. Monitor loop: every `STATUS_CHECK_INTERVAL` s, `IdleMonitor.observe(active_peer_count())`:
   `WARN` → `notifier.idle_warning()`; `STOP` → `notifier.stopped_idle()`, `tailscale logout`,
   terminate `tailscaled`, exit code 0.
8. `SIGTERM` → `notifier.stopped()`, `tailscale logout`, terminate `tailscaled`, exit code 0.

`IdleMonitor(inactivity_timeout, check_interval, warning_before=120)`:
- `observe(n)`: `n > 0` → reset idle time and warning flag, return `NONE`.
  Otherwise idle time += `check_interval`; if idle time ≥ timeout → `STOP`; else if not yet warned
  and `timeout - idle time ≤ warning_before` → `WARN` (once per idle period); else `NONE`.
  (Defaults 600/60: warns at 480 s idle, stops at 600 s.)

`Tailscale.active_peer_count()`: runs `tailscale status --json`, counts entries of `Peer` (object,
may be missing/null) whose `Active` is `true`; any error (non-zero exit, bad JSON) → 0.

`Notifier` (all methods no-ops when notifications are disabled or the needed text is empty;
Telegram errors are logged and swallowed):
- `render(text, public_ip=None)` replaces `{{HOSTNAME}}` with `TAILSCALE_HOSTNAME` and
  `{{PUBLIC_IP}}` with `public_ip` (if given).
- `ready(ip)` → edit `TG_MESSAGE_ID` with `TG_READY_TEXT` + `TG_READY_MARKUP`.
- `idle_warning()` → send `TG_IDLE_WARNING_TEXT`, silent.
- `stopped_idle()` → send `TG_STOPPED_TEXT` + `TG_STOPPED_MARKUP` (not silent), then edit the card
  with `TG_STOPPED_CARD_TEXT` (no markup).
- `stopped()` → edit the card with `TG_STOPPED_CARD_TEXT` (no markup).

`TelegramClient(token, api_base="https://api.telegram.org", session=None)` posts JSON to
`{api_base}/bot{token}/{method}` with a 10 s timeout:
- `send_message(chat_id, text, reply_markup=None, silent=False)` → `sendMessage` with
  `chat_id, text, parse_mode="HTML", disable_web_page_preview=true, disable_notification=silent`
  (+ `reply_markup` parsed from the JSON string when given).
- `edit_message(chat_id, message_id, text, reply_markup=None)` → `editMessageText` with
  `chat_id, message_id, text, parse_mode="HTML", disable_web_page_preview=true` (+ `reply_markup`).
- Both return `True` on HTTP 200 with `"ok": true`, else log and return `False`; never raise.

Environment (`AgentConfig.from_env`): `TAILSCALE_HOSTNAME`, `TAILSCALE_TOKEN_SECRET_ID`,
`TAILSCALE_TOKEN_SECRET_REGION` (required); `INACTIVITY_TIMEOUT` (default 600),
`STATUS_CHECK_INTERVAL` (default 60), `TG_BOT_TOKEN_SECRET_ID`, `TG_BOT_TOKEN_SECRET_REGION`,
`TG_API_BASE` (default `https://api.telegram.org`) and the `TG_*` vars of §4.4 (optional).

Image (`docker/Dockerfile`): `FROM amazonlinux:2023`; install `python3`, `python3-pip` and
`tailscale` (repo `https://pkgs.tailscale.com/stable/amazon-linux/2023/tailscale.repo`);
`pip install --no-cache-dir -r requirements.txt`; copy `node_agent` to `/opt/node_agent`;
`WORKDIR /opt`; `CMD ["python3", "-m", "node_agent"]`. The `aws` CLI and the old shell scripts are
removed. The task definition health check (`tailscale status | grep ...`) keeps working.

Tests: `cd docker/node_agent && PYTHONPATH=src python3 -m unittest discover -s tests -t .`

## 6. Infrastructure

- `cloudformation/vpn-configurer-lambda.yml`: API Gateway integration `TimeoutInMillis: 29000`;
  new parameters `EnvTgBotTokenSecretId` (default `vpn-tg-bot-token`), `EnvMetricsEnabled`
  (default `"true"`), `EnvConfigCacheTtlSec` (default `"300"`) → Lambda env `BOT_TOKEN_SECRET_ID`,
  `METRICS_ENABLED`, `CONFIG_CACHE_TTL_SEC`; Lambda role may `GetSecretValue` on the bot-token
  secret. `BOT_TOKEN` stays (fallback, removed in Phase 3).
- `cloudformation/ecs-vpn-server.yml`: parameter `TgBotTokenSecretId` (default `vpn-tg-bot-token`);
  container env `TG_BOT_TOKEN_SECRET_ID` and `TG_BOT_TOKEN_SECRET_REGION`
  (= `TailscaleAuthTokenSecretRegion`); task role may `GetSecretValue` on that secret.
- `.github/workflows/deploy-tgbot-lambda.yml`: before the stack deploy, create secret
  `${{ vars.TG_BOT_TOKEN_SECRET_ID || 'vpn-tg-bot-token' }}` from `secrets.TG_BOT_TOKEN` in the
  deploy region if it does not exist (same pattern as the webhook secret); pass the new parameters.
- `.github/workflows/deploy-vpn-ecs-resources.yml`: same "create if missing" step in
  `CONFIG_BASE_REGION`; pass `TgBotTokenSecretId`.
- New `.github/workflows/node-agent-tests.yml`: on push / pull request touching `docker/**` or the
  workflow itself: Python 3.9, `pip install -r docker/requirements.txt`, run the test command of §5.

## 7. Contracts

```java
// org.github.akarkin1.metrics
public enum MetricComponent { S3, ECS, TELEGRAM }
public interface RequestMetrics {
  void start(String updateKind);
  <T> T time(MetricComponent component, Supplier<T> action);
  void time(MetricComponent component, Runnable action);
  void finish();
}
public class EmfRequestMetrics implements RequestMetrics {
  public EmfRequestMetrics(boolean enabled, Clock clock, Consumer<String> output);
}

// org.github.akarkin1.ui
public interface NodeLauncher {
  void launchInNewMessage(UiContext context, String regionId, String hostName);
}
public record UiComponents(UiRouter router, NodeLauncher nodeLauncher) {}
UiConfigurer.configure(AbsSender, Translator, TailscaleNodeService, Authorizer, RequestMetrics) → UiComponents

// org.github.akarkin1.ui.messenger
public record RenderedMessage(String text, InlineKeyboardMarkup keyboard) {}
public class ScreenRenderer { ScreenRenderer(Translator); public RenderedMessage render(UiContext, Screen); }
public class NodeNotifications {          // (LaunchScreens, ScreenRenderer)
  public static final String HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}";
  public static final String PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}";
  public Map<String, String> build(UiContext context, Integer messageId, String regionId);
}
UiMessenger.send(UiContext, Screen) → Integer   // id of the sent message
TelegramUiMessenger(AbsSender, ScreenRenderer, RequestMetrics)

// org.github.akarkin1.ui.controller
public class LaunchController implements NodeLauncher {   // (TailscaleNodeService, Authorizer, UiMessenger, LaunchScreens, NodeNotifications)
  public void launch(UiContext context, Integer messageId, String regionId);
  public void launchInNewMessage(UiContext context, String regionId, String hostName);
}

// org.github.akarkin1.ui.screen.LaunchScreens – see §4.3
public Screen waiting(String regionId);
public Screen ready(String hostName, String regionId, String publicIp);
public Screen idleWarning(String hostName);
public Screen stopped(String hostName, String regionId);
public Screen stoppedCard(String hostName, String regionId);

// existing code
TailscaleNodeService.runNode(String userRegion, String userTgId, String userHostName, Map<String, String> environment) → TaskInfo
TailscaleNodeService.toRegionId(String userRegion) → String   // region id for a region id or city name
EcsManager.startTask(Region, String hostName, Map<String, String> tags, Map<String, String> environment) → TaskInfo
RunNodeCommand(TailscaleNodeService, MessageConsumer, NodeLauncher)
CachedS3TaskConfigService(TaskConfigService delegate, Duration ttl, Clock clock)
SecretManagerRequestAuthenticator(SecretsManagerClient, String secretTokenId, Duration ttl, Clock clock)
S3ConfigManager.create(S3Configuration, RequestMetrics)
S3TaskConfigService.create(S3Configuration, RequestMetrics)
EcsManagerImpl(TaskConfigService, EcsClientPool, Ec2ClientPool, EcsConfiguration, Map<String,String> regionToCities, ExecutorService executor, RequestMetrics metrics)
BotCommunicator(AbsSender, Translator, RequestMetrics)
public class BotTokenResolver { BotTokenResolver(SecretsManagerClient); public String resolve(String secretId, String fallbackToken); }  // org.github.akarkin1.config
ConfigManager.getConfigCacheTtl() → Duration; ConfigManager.isMetricsEnabled() → boolean; ConfigManager.getBotTokenSecretId() → String
```

```python
# docker/node_agent/config.py
@dataclass(frozen=True)
class AgentConfig:            # fields: hostname, tailscale_secret_id, tailscale_secret_region,
    ...                       # inactivity_timeout, status_check_interval, bot_token_secret_id,
    @staticmethod             # bot_token_secret_region, telegram_api_base, chat_id, message_id (int|None),
    def from_env(env) -> "AgentConfig": ...   # ready_text, ready_markup, idle_warning_text,
                              # stopped_text, stopped_markup, stopped_card_text (str|None)
# docker/node_agent/secrets.py
def get_secret(secret_id: str, region: str, client_factory=boto3.client) -> str
# docker/node_agent/telegram.py
class TelegramClient:
    def __init__(self, token, api_base="https://api.telegram.org", session=None)
    def send_message(self, chat_id, text, reply_markup=None, silent=False) -> bool
    def edit_message(self, chat_id, message_id, text, reply_markup=None) -> bool
# docker/node_agent/notifier.py
class Notifier:
    def __init__(self, config: AgentConfig, telegram: Optional[TelegramClient])
    enabled: bool (property)
    def render(self, text, public_ip=None) -> str
    def ready(self, public_ip) / idle_warning(self) / stopped_idle(self) / stopped(self) -> None
# docker/node_agent/monitor.py
class Action(Enum): NONE, WARN, STOP
class IdleMonitor:
    def __init__(self, inactivity_timeout, check_interval, warning_before=120)
    def observe(self, active_peers: int) -> Action
# docker/node_agent/tailscale.py
class Tailscale:
    def __init__(self, run=subprocess.run, popen=subprocess.Popen, sleep=time.sleep)
    def start_daemon(self) -> None
    def up(self, auth_key, hostname, attempts=120, delay=1.0) -> bool
    def active_peer_count(self) -> int
    def logout(self) -> None
    def stop_daemon(self) -> None
# docker/node_agent/agent.py
def fetch_public_ip(session=None) -> str
def run(env=os.environ) -> int          # exit code; __main__.py: sys.exit(run())
```

## 8. Acceptance criteria (name the AC in each test)

Java:
- AC-1 `LaunchController.launch` follows §4.1: not allowed; `starting` before region check;
  region unavailable; `runNode` gets the env from `NodeNotifications` and `hostName` null;
  `runNode` throws → `failed`; success → `waiting`; no other node-service calls.
- AC-2 `launchInNewMessage` sends `starting` as a new message and uses its id for the
  notifications and the following edits; passes `hostName` through.
- AC-3 `RunNodeCommand` keeps its validation messages and, when valid, calls the launcher with
  `toRegionId(...)`, the given hostname and a context from `TgRequestContext`.
- AC-4 `LaunchScreens` new/changed screens match §4.3; `%s` count = params, no null params,
  templates format without throwing.
- AC-5 `NodeNotifications.build` returns exactly the §4.4 keys with placeholder-filled texts in the
  context language; markup JSON has `inline_keyboard` with the right buttons; `*_MARKUP` omitted
  for screens without buttons; total size < 8192.
- AC-6 `ScreenRenderer` translates, escapes params, translates labels, null keyboard when empty;
  `TelegramUiMessenger.send` returns the sent message id and times Telegram calls.
- AC-7 `EmfRequestMetrics`: one valid EMF JSON line with the 4 metrics and the dimension;
  accumulates repeated timings; disabled → no output and actions still run; `finish()` without
  `start()` → no output; thread-safe accumulation.
- AC-8 `CachedS3TaskConfigService`: within TTL one delegate call; after TTL reloads; TTL 0 → every
  call delegates; runtime parameters cached per region.
- AC-9 `EcsManagerImpl.listTasks` queries regions concurrently and returns tasks in region order.
- AC-10 `SecretManagerRequestAuthenticator` fetches the secret once per TTL and still rejects
  missing/blank/wrong tokens.
- AC-11 `BotTokenResolver`: secret id set → secret value; blank → fallback.
- AC-12 Every `ui.*` key used in `src/main/java` exists in both properties files; removed keys are
  gone (existing `UiMessagesTest` keeps passing).

Python:
- AC-P1 `IdleMonitor`: warns once at 480 s idle (600/60), stops at 600 s, activity resets both.
- AC-P2 `Tailscale.active_peer_count`: counts `Active` peers; 0 for no peers, null `Peer`,
  non-zero exit, invalid JSON.
- AC-P3 `TelegramClient`: request URL/payload per §5; `reply_markup` sent as an object; returns
  False (no exception) on HTTP error, `"ok": false` and network exceptions.
- AC-P4 `Notifier`: placeholders replaced; disabled without token/chat/message; each method sends
  the documented calls (silent warning, stopped = send + edit card, SIGTERM = edit card only).
- AC-P5 `AgentConfig.from_env`: defaults, ints parsed, optional vars None when absent.
- AC-P6 `agent.run`: secret failure → 1; `up` failure → 1; idle STOP path notifies, logs out,
  returns 0 (with fakes for Tailscale, Telegram and sleep).

## 9. Definition of done

- `mvn -B verify` passes; Python tests pass with the command of §5.
- Only files listed in §10 (plus this spec, `docs/roadmap.md`, `CLAUDE.md`) changed.
- Every AC has a test. No TODOs, no dead code, no commented-out code.

## 10. Tasks and file ownership

| Task | Files |
|---|---|
| T1 Foundation (Java contracts) | all signature changes of §7 with compilable stubs for new behaviour; new `metrics/*`, `ui/NodeLauncher`, `ui/UiComponents`, `ui/messenger/RenderedMessage`, `ui/messenger/ScreenRenderer`, `ui/messenger/NodeNotifications`, `config/BotTokenResolver` (stubs); `ConfigManager` getters (implemented); §4.7 removals incl. `application.yml`/`YamlApplicationConfiguration`; `runNode`/`startTask` env pass-through and `toRegionId` (implemented); handler wiring adapted so the project compiles; new message keys (§4.3) in both files |
| T2 Java tests | `src/test/java/**` (new and updated tests; may update existing tests whose constructors changed) |
| T3 Launch flow | `ui/controller/LaunchController`, `ui/messenger/**`, `ui/screen/LaunchScreens`, `ui/UiConfigurer`, `dispatcher/command/RunNodeCommand`, `tailscale/TailscaleEcsNodeService`, removal of unused message keys |
| T4 Speed, metrics, secrets | `metrics/**`, `TailscaleVpnLambdaHandler`, `ecs/EcsManagerImpl`, `config/CachedS3TaskConfigService`, `config/S3TaskConfigService`, `config/BotTokenResolver`, `s3/S3ConfigManager`, `auth/SecretManagerRequestAuthenticator`, `auth/RequestAuthenticatorConfigurer`, `auth/s3/PermissionsServiceConfigurer`, `tailscale/TailscaleEcsNodeServiceConfigurer`, `tg/BotCommunicator` |
| T5 Node agent | `docker/node_agent/**` except `tests/`, `docker/Dockerfile`, `docker/requirements.txt`, removal of `docker/scripts/*` |
| T6 Python tests | `docker/node_agent/tests/**` (written from this spec only) |
| T7 Infrastructure | `cloudformation/*.yml`, `.github/workflows/*.yml` |

T2–T7 run in parallel after T1.

## 11. Deploy checklist

1. Merge the PR → CI deploys the Lambda code.
2. Run "Deploy VPN Configurer Lambda Resources" (creates the bot-token secret, new parameters,
   timeout).
3. For **each** supported region run "Deploy Tailscale ECS Resources" with CloudFormation **and**
   Docker enabled.
Mixed versions are safe: an old image ignores the `TG_*` vars (user taps Menu); a new image without
`TG_*` vars runs with notifications disabled.

## 12. Manual test checklist

1. Tap a region → starting → waiting (with Menu) → the same message becomes the node card with the
   real hostname and IP (sent by the container).
2. Leave the node idle → after ~8 min a silent warning, after ~10 min a "stopped" message with
   "Start again"; the card shows "⚪ … Stopped".
3. Stop a task in the ECS console → the card shows "Stopped", no new message.
4. `/runNodeIn <city>` → a new progress message that behaves like 1.
5. Russian client → all node messages in Russian.
6. CloudWatch → namespace `vpnbot` shows the 4 metrics; set `METRICS_ENABLED=false` → no more
   EMF lines.
7. Refresh/home taps feel faster; Telegram no longer retries (no duplicate-update log lines).

## 13. Decision log

- D-1 (T5) Missing required agent env vars → `AgentConfig.from_env` raises `ValueError`; `run` returns 1.
  `chat_id` is kept as a string.
- D-2 (T5) `Notifier.render` HTML-escapes the hostname and IP (CLAUDE.md rule; normal values unchanged).
- D-3 (T5) Every `tailscale` CLI call has a 30 s timeout (a hung `status` must not keep a node alive).
- D-4 (review) `Tailscale.up` also stops after 300 s in total (`Tailscale(..., monotonic=time.monotonic)`
  injectable), so a node that can't reach the Tailscale control server doesn't run for up to an hour.
- D-5 (review) If `up` fails, the agent edits the progress message to the stopped card
  (`notifier.stopped()`) before exiting with 1, so the user isn't left on "waiting".
- D-6 (T3) Two message keys that were already unused before this phase were removed with the others
  (`command.assign-roles.usage-note.without-roles`, `common.permissions.action-not-allowed.error`).
- D-7 (review, blocker) `EcsClientPool` and `Ec2ClientPool` use `ConcurrentHashMap`: parallel `listTasks`
  hit `HashMap.computeIfAbsent` concurrently (ConcurrentModificationException on cold start).
  Both files are added to T4's ownership.
- D-8 (review) A task whose ENI has no public IP yet (no association) or whose ENI lookup fails gets
  `publicIp = null` instead of failing the whole listing (users can now open the menu while a node is
  still starting or stopping).
- D-9 (review) The agent parses `TG_MESSAGE_ID`/`TG_CHAT_ID` leniently: an invalid value logs a
  warning and disables notifications; it never stops the node from starting.
- D-10 (review) The deploy workflows update the bot-token secret (`put-secret-value`) when it already
  exists, so rotating `TG_BOT_TOKEN` in GitHub takes effect on the next deploy.
- D-11 (review) The node container gets `StopTimeout: 60` so the SIGTERM path (card edit, logout,
  daemon stop) can finish before ECS kills it.
- D-12 `.gitignore` ignores Python bytecode caches (`__pycache__/`, `*.pyc`).
- D-13 (feedback) Node agent layout: production package in `docker/node_agent/src/node_agent`, tests in
  `docker/node_agent/tests` (tests are not inside the production source tree). Paths in §5, §7 and §10
  that say `docker/node_agent/...` refer to this layout; the test command is
  `cd docker/node_agent && PYTHONPATH=src python3 -m unittest discover -s tests -t .`.
- D-14 (prod validation) The 29 s integration timeout never reached production: the HTTP API stage
  still served the deployment snapshot created with the stack (0.6 s timeout), because CloudFormation
  does not redeploy when the integration changes. Every request over ~600 ms got a 5xx, Telegram
  re-delivered the update (dropped by deduplication, but each re-delivery could start another cold
  Lambda). Fixed by renaming the Deployment resource (`ApiGatewayDeployment20261010`) so a new
  deployment is created; the template comment says to rename it on every route/integration change.
