# Spec: UI/UX redesign – Phase 1 (button UI on top of the existing bot)

Status: approved for implementation · Branch: `feature/ui-ux-phase1`

## 1. Goal

Users can use the bot without knowing any text command. `/start`, `/menu` or any plain text opens
one "home" message with inline buttons; tapping buttons edits that message in place. Starting a
node is one tap on a region button and shows its progress in one message that ends as a node card.

## 2. Non-goals (do NOT do any of these)

- No change to the behaviour or output of any existing text command (`/help`, `/runNodeIn`,
  `/listRunningNodes`, `/supportedRegions`, `/version`, admin commands). `CommandDispatcher`,
  everything in `dispatcher/**`, `BotCommunicator`, `auth/**`, `ecs/**`, `ec2/**`, `s3/**`,
  `docker/**` and `cloudformation/**` stay untouched.
- No new features: no Stop button, no notifications, no access requests, no admin UI, no deep links,
  no custom node names from the UI, no "other region" requests.
- No new dependencies, no refactoring of existing code, no renames, no formatting-only changes.
- No persistent state (no DB, no S3 writes). All UI state lives in callback data.
- No reply keyboard (only inline keyboards).

## 3. Ground rules

- Follow `CLAUDE.md`. Imitate existing code: `RunNodeCommand` (constructor injection,
  `@RequiredArgsConstructor`, `@Log4j2`), `TailscaleEcsNodeServiceConfigurer` (wiring),
  `HelpCommandV2Test` (test style: JUnit 5, `@ExtendWith(MockitoExtension.class)`, `@Mock`).
- Java 21. Records for value types. Two-space indentation like the existing code.
- Implement exactly the contracts in §6 (names, packages, signatures). Private helpers are fine.
- Only touch files listed for your task in §9.
- **Escalation:** if something in this spec cannot be implemented as written, stop and report
  `SPEC DEVIATION: <what> / <why> / <proposed option>` instead of improvising.

## 4. Screens

Notation: templates are Java strings; `${key}` is translated by the existing `Translator`; `%s` is
filled from `params` in order (params are HTML-escaped by the messenger). Text is sent with
`parse_mode=HTML`. `—` means the literal em dash used when a value is null/blank.
`MENU` = `Button.action("🏠 ${ui.button.menu}", UiAction.home())`.
`LINKS` row = `[Button.link("📖 ${ui.button.exit-node-guide}", Links.EXIT_NODE_GUIDE),
Button.link("⬇️ ${ui.button.get-tailscale}", Links.DOWNLOAD)]`.

Shared formatting (`NodeFormat`):
- status emoji from `TaskInfo.getState()`: `"HEALTHY"` → `🟢`, `"UNHEALTHY"` → `🔴`, anything else
  (incl. null) → `🟡`.
- a null/blank value is shown as `—`.

Region labels (`RegionLabels`): `label(id)` = `flag(id) + " " + city(id)`;
`city(id)` = city from `aws.region-cities`, or the id itself if absent;
`flag(id)` = flag emoji built from the 2-letter ISO code in `aws.region-countries`
(each letter A–Z → code point `0x1F1E6 + (letter - 'A')`), or `🌐` if the code is absent/invalid.
Example: `eu-central-1` → `🇩🇪 Frankfurt`.

### 4.1 Home (`HomeScreen.home(HomeModel)`)

Display name = `firstName` if not blank, else `"@" + username` if not blank, else none.

Template is built by appending, in this order:
1. Greeting: `"👋 ${ui.home.greeting}, %s!"` with the display name, or `"👋 ${ui.home.greeting}!"`
   (no param) when there is no display name.
2. If `!canListNodes && !canRunNodes`: `"\n\n${ui.home.no-access}"`.
3. If `canListNodes`: `"\n\n<b>${ui.home.your-nodes}</b>"` (or `${ui.home.all-nodes}` when
   `allNodes`), then either `"\n${ui.home.no-nodes}"` when the list is empty, or per node, in the
   given order: `"\n%s <b>%s</b> · %s · <code>%s</code>"` with params
   `[statusEmoji, hostName|—, label(region.id())|—, publicIp|—]`.
4. If `canRunNodes`: `"\n\n<b>${ui.home.start-node}</b>"`, plus `"\n${ui.home.no-regions}"` when
   there are no regions.

Keyboard:
- If `canRunNodes`: one button per region, sorted by `city(id)` (case-insensitive), 3 per row:
  `Button.action(label(id), UiAction.run(id))`.
- Always last row: `[Button.action("🔄 ${ui.button.refresh}", UiAction.home()),
  Button.action("❓ ${ui.button.help}", UiAction.help())]`.

### 4.2 Launch (`LaunchScreens`)

| Method | Template | Params | Keyboard |
|---|---|---|---|
| `starting(regionId)` | `"🚀 ${ui.launch.starting} %s…\n⏳ ${ui.launch.step.submitting}"` | `[label]` | none |
| `waiting(regionId)` | `"🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}"` | `[label]` | none |
| `ready(TaskInfo node)` | `"%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}"` | `[statusEmoji, hostName\|—, label(region.id())\|—, publicIp\|—]` | `LINKS`, `[MENU]` |
| `stillStarting(regionId)` | `"🟡 ${ui.launch.still-starting}\n📍 %s"` | `[label]` | `[MENU]` |
| `failed(regionId)` | `"🔴 ${ui.launch.failed}\n📍 %s"` | `[label]` | `[Button.action("🔁 ${ui.button.try-again}", UiAction.run(regionId)), MENU]` |
| `regionUnavailable()` | `"⚠️ ${ui.launch.region-unavailable}"` | `[]` | `[MENU]` |
| `notAllowed()` | `"⛔ ${ui.launch.not-allowed}"` | `[]` | `[MENU]` |

### 4.3 Help (`HelpScreen.help()`) and error (`ErrorScreen.generic()`)

- Help: `"❓ <b>${ui.help.title}</b>\n\n${ui.help.body}"`, no params, keyboard `LINKS`, `[MENU]`.
- Generic error: `"⚠️ ${ui.error.generic}"`, no params, keyboard `[MENU]`.

### 4.4 Links (`Links`)

- `EXIT_NODE_GUIDE = "https://tailscale.com/docs/features/exit-nodes"`
- `DOWNLOAD = "https://tailscale.com/download"`

### 4.5 Message keys

Both `messages.properties` (EN) and `messages_ru.properties` (RU) get exactly these keys, appended
at the end under a `# UI (phase 1)` comment. Files stay ASCII: escape every non-ASCII character as
`\uXXXX` (uppercase hex, like the existing file). No emoji, no `%`, no `<`/`>`/`&` in values.
`\n` inside a value is a newline.

| Key | EN | RU |
|---|---|---|
| ui.home.greeting | Hi | Привет |
| ui.home.no-access | You don't have access to this bot yet. Please ask @karkin_ai to grant it. | У вас пока нет доступа к этому боту. Попросите @karkin_ai его выдать. |
| ui.home.your-nodes | Your nodes | Ваши узлы |
| ui.home.all-nodes | Running nodes | Запущенные узлы |
| ui.home.no-nodes | No running nodes. | Нет запущенных узлов. |
| ui.home.start-node | Start a VPN node | Запустить VPN-узел |
| ui.home.no-regions | No regions are available right now. | Сейчас нет доступных регионов. |
| ui.button.refresh | Refresh | Обновить |
| ui.button.help | Help | Помощь |
| ui.button.menu | Menu | Меню |
| ui.button.try-again | Try again | Повторить |
| ui.button.exit-node-guide | Exit node guide | Инструкция |
| ui.button.get-tailscale | Get Tailscale | Скачать Tailscale |
| ui.launch.starting | Starting a node in | Запускаем узел в регионе |
| ui.launch.step.submitting | Submitting the task | Отправляем задачу |
| ui.launch.step.submitted | Task started | Задача запущена |
| ui.launch.step.waiting | Waiting for Tailscale to come up (this may take a few minutes) | Ждём запуска Tailscale (это может занять несколько минут) |
| ui.launch.still-starting | The node is taking longer than usual to start. Check its status in the menu in a minute. | Узел запускается дольше обычного. Проверьте его статус в меню через минуту. |
| ui.launch.failed | Couldn't start the node. Please try again or contact @karkin_ai. | Не удалось запустить узел. Попробуйте ещё раз или напишите @karkin_ai. |
| ui.launch.region-unavailable | This region is not available anymore. | Этот регион больше недоступен. |
| ui.launch.not-allowed | You are not allowed to start nodes. | У вас нет прав на запуск узлов. |
| ui.node.auto-stop | Stops automatically after 10 minutes with no devices connected | Остановится автоматически через 10 минут без подключённых устройств |
| ui.node.connect-hint | In the Tailscale app, select this node as your exit node. | В приложении Tailscale выберите этот узел в качестве exit node. |
| ui.error.generic | Something went wrong. Please try again later or contact @karkin_ai. | Что-то пошло не так. Попробуйте позже или напишите @karkin_ai. |
| ui.help.title | How it works | Как это работает |
| ui.help.body | (see below) | (see below) |

`ui.help.body` EN (`\n` = newline, `•` = `•`, `–` = `–`):
```
This bot starts your personal Tailscale VPN node in the AWS region of your choice.\n\n• Tap a city in the menu to start a node. It takes a couple of minutes.\n• In the Tailscale app, select the node as your exit node.\n• The node stops automatically after 10 minutes with no devices connected, so you don't need to stop it.\n\nTo connect, your Tailscale account must be invited to our network – ask @karkin_ai.\nAll text commands: /help
```
`ui.help.body` RU:
```
Бот запускает ваш личный VPN-узел Tailscale в выбранном регионе AWS.\n\n• Нажмите на город в меню, чтобы запустить узел. Это займёт пару минут.\n• В приложении Tailscale выберите узел в качестве exit node.\n• Узел останавливается сам через 10 минут без подключённых устройств, выключать его не нужно.\n\nЧтобы подключиться, ваш аккаунт Tailscale должен быть приглашён в нашу сеть – напишите @karkin_ai.\nВсе текстовые команды: /help
```

## 5. Behaviour

### 5.1 Routing (`UiRouter`)

`canHandle(update)` is true when:
- the update has a callback query, or
- the update has a message and its text is null, or does not start with `/`, or its first
  whitespace-separated token is exactly `/start` or `/menu`.
Otherwise false, and the update goes to `CommandDispatcher` exactly as today.

`handle(update)`:
1. `context = UiContext.fromUpdate(update)`.
2. Message → `homeController.showHome(context)` (new message).
3. Callback query → first `messenger.answerCallback(callbackQuery.getId())`; then, with
   `messageId = callbackQuery.getMessage().getMessageId()`:
   - callback message is null → `homeController.showHome(context)`;
   - data does not decode → `homeController.refreshHome(context, messageId)`;
   - `HOME` → `refreshHome`; `HELP` → `homeController.showHelp(context, messageId)`;
     `RUN` → `launchController.launch(context, messageId, action.arg())`.
4. Any `RuntimeException` from steps 2–3 is logged and answered with
   `messenger.send(context, errorScreen.generic())`; a failure of that send is logged and swallowed.
   `handle` never throws.

### 5.2 Home (`HomeController`)

Model: if `context.username()` is null → no permissions, no service calls. Otherwise
`canListNodes = hasPermission(username, LIST_NODES)`, `canRunNodes = hasPermission(username, RUN_NODES)`,
`allNodes = hasPermission(username, ROOT_ACCESS)`;
`nodes = canListNodes ? nodeService.listTasks(allNodes ? null : username) : List.of()`;
`regionIds = canRunNodes ? new ArrayList<>(nodeService.getSupportedRegionIds()) : List.of()`.
(Same visibility rule as `ListNodesCommand`.)
- `showHome(ctx)` → `messenger.send(ctx, homeScreen.home(model))`
- `refreshHome(ctx, messageId)` → `messenger.edit(ctx, messageId, homeScreen.home(model))`
- `showHelp(ctx, messageId)` → `messenger.edit(ctx, messageId, helpScreen.help())`

### 5.3 Launch (`LaunchController.launch(ctx, messageId, regionId)`)

1. Username null or no `RUN_NODES` → edit `notAllowed()`; stop.
2. `regionId` not in `nodeService.getSupportedRegionIds()` → edit `regionUnavailable()`; stop.
3. Edit `starting(regionId)`.
4. `task = nodeService.runNode(regionId, username, null)`; a `RuntimeException` → log, edit
   `failed(regionId)`; stop.
5. Edit `waiting(regionId)`.
6. `status = nodeService.checkNodeStatus(task)`; a `RuntimeException` → log, edit `failed(regionId)`; stop.
7. `HEALTHY` → `nodeService.getFullTaskInfo(task.getRegion(), task.getCluster(), task.getId())`:
   present → edit `ready(info)`, empty → edit `stillStarting(regionId)`.
   `UNKNOWN` → edit `stillStarting(regionId)`. `UNHEALTHY` → edit `failed(regionId)`.

### 5.4 Messenger (`TelegramUiMessenger`)

- Text = `translator.translate(ctx.languageCode(), screen.template(), escapedParams)` where each
  param is `Html.escape(String.valueOf(param))`.
- Button label = `translator.translate(ctx.languageCode(), button.label())`.
- `send` → `SendMessage` (chatId, text, `parseMode = "HTML"`, `disableWebPagePreview = true`,
  `replyMarkup` = inline keyboard, or none when the screen keyboard is empty).
- `edit` → `EditMessageText` with the same fields plus `messageId`. A `TelegramApiRequestException`
  whose `getApiResponse()` contains `message is not modified` is logged at debug and ignored.
  Other `TelegramApiException`s propagate (use `@SneakyThrows` like `BotCommunicator`).
- `answerCallback(id)` → `AnswerCallbackQuery` with that id; any `TelegramApiException` is logged
  and swallowed (best effort).
- Inline button: `callbackData` set for action buttons, `url` set for link buttons.
- `Html.escape(s)`: `&`→`&amp;`, `<`→`&lt;`, `>`→`&gt;`, `"`→`&quot;`; null → `""`.

### 5.5 Request context (`TgRequestContext.initContext`)

Callback query updates: user = `callbackQuery.getFrom()`, chatId = `callbackQuery.getMessage().getChatId()`
(or `from.getId()` if the message is null). Message updates: unchanged. Any other update: all
fields reset to null (language code to the default). Getters unchanged.

### 5.6 Lambda handler (`TailscaleVpnLambdaHandler`)

- Static block: create one `ResourceBasedTranslator`, use it for `BotCommunicator` and for
  `UI_ROUTER = new UiConfigurer().configure(sender, translator, nodeService, authorizer)`.
- `handleUpdate`: right after `EVENTS_REGISTRY.registerEvent(update)` add
  `if (UI_ROUTER.canHandle(update)) { UI_ROUTER.handle(update); return; }`. Nothing else changes.

### 5.7 Telegram command menu (`deploy-tgbot-lambda.yml`)

Append a final step "Register Telegram bot commands" that calls `setMyCommands` twice with curl
(`-sS --fail`, JSON body), token passed through `env: TG_BOT_TOKEN: ${{ secrets.TG_BOT_TOKEN }}`:
- default: `menu` – "Open the menu", `help` – "List all text commands"
- `language_code: "ru"`: `menu` – "Открыть меню", `help` – "Список текстовых команд"

### 5.8 Configuration

- `YamlApplicationConfiguration.AWSConfiguration`: add `private Map<String, String> regionCountries;`.
- `application.yml` under `aws`: add `region-countries` with an entry for every `region-cities`
  key; add the regions `'mx-central-1': Mexico` (MX), `'ap-east-2': Taipei` (TW),
  `'ap-southeast-6': Auckland` (NZ) to `region-cities`. Codes: us-* US, af-south-1 ZA,
  ap-east-1 HK, ap-south-1/ap-south-2 IN, ap-southeast-3 ID, ap-southeast-5 MY, ap-southeast-4 AU,
  ap-southeast-7 TH, ap-northeast-1/ap-northeast-3 JP, ap-northeast-2 KR, ap-southeast-1 SG,
  ap-southeast-2 AU, ca-* CA, eu-central-1 DE, eu-west-1 IE, eu-west-2 GB, eu-south-1 IT,
  eu-west-3 FR, eu-south-2 ES, eu-north-1 SE, eu-central-2 CH, il-central-1 IL, me-south-1 BH,
  me-central-1 AE, sa-east-1 BR, us-gov-* US. Existing entries are not changed.
- `TailscaleNodeService`: add `Set<String> getSupportedRegionIds();`
  (`TailscaleEcsNodeService`: `return ecsManager.getSupportedRegions();`).

## 6. Contracts

All new code lives under `src/main/java/org/github/akarkin1/ui`.

```java
// ui
public record UiAction(Type type, String arg) {
  public enum Type { HOME, HELP, RUN }
  // compact ctor: RUN requires non-blank arg; HOME/HELP require arg == null (else IllegalArgumentException)
  public static UiAction home();
  public static UiAction help();
  public static UiAction run(String regionId);
  public String encode();                        // "HOME" | "HELP" | "RUN:<regionId>"; IllegalArgumentException if > 64 UTF-8 bytes
  public static Optional<UiAction> decode(String data); // empty for null/blank/unknown type/HOME|HELP with ':'/RUN without arg
}
public record UiContext(Long chatId, String username, String firstName, String languageCode) {
  public static UiContext fromUpdate(Update update); // message or callback query; languageCode defaults to "en-US" when blank
}
public class UiRouter {                          // @Log4j2 @RequiredArgsConstructor
  UiRouter(HomeController, LaunchController, UiMessenger, ErrorScreen); // public ctor
  public boolean canHandle(Update update);
  public void handle(Update update);
}
public class UiConfigurer {
  public UiRouter configure(AbsSender sender, Translator translator,
                            TailscaleNodeService nodeService, Authorizer authorizer);
}

// ui.screen
public record Button(String label, String callbackData, String url) {
  public static Button action(String label, UiAction action);
  public static Button link(String label, String url);
}
public record Screen(String template, List<Object> params, List<List<Button>> keyboard) {} // defensive copies
public record HomeModel(String firstName, String username, boolean canListNodes, boolean canRunNodes,
                        boolean allNodes, List<TaskInfo> nodes, List<String> regionIds) {}
public final class Links { public static final String EXIT_NODE_GUIDE, DOWNLOAD; }
final class NodeFormat { static String statusEmoji(String state); static String orDash(String value); }
public class RegionLabels {                      // ctor (Map<String,String> regionCities, Map<String,String> regionCountries), null maps = empty
  public String city(String regionId);
  public String flag(String regionId);
  public String label(String regionId);
}
public class HomeScreen    { HomeScreen(RegionLabels); public Screen home(HomeModel model); }
public class LaunchScreens { LaunchScreens(RegionLabels); starting, waiting, ready, stillStarting, failed, regionUnavailable, notAllowed → Screen }
public class HelpScreen    { public Screen help(); }
public class ErrorScreen   { public Screen generic(); }

// ui.messenger
public interface UiMessenger {
  void send(UiContext context, Screen screen);
  void edit(UiContext context, Integer messageId, Screen screen);
  void answerCallback(String callbackQueryId);
}
public class TelegramUiMessenger implements UiMessenger { TelegramUiMessenger(AbsSender, Translator); }
public final class Html { public static String escape(String value); }

// ui.controller
public class HomeController {   // (TailscaleNodeService, Authorizer, UiMessenger, HomeScreen, HelpScreen)
  public void showHome(UiContext context);
  public void refreshHome(UiContext context, Integer messageId);
  public void showHelp(UiContext context, Integer messageId);
}
public class LaunchController { // (TailscaleNodeService, Authorizer, UiMessenger, LaunchScreens)
  public void launch(UiContext context, Integer messageId, String regionId);
}
```

## 7. Acceptance criteria (each needs at least one test; name the AC in `@DisplayName`)

- AC-1 `UiAction` encode/decode round-trips for HOME, HELP and RUN; decode rejects null, blank,
  unknown type, `HOME:x`, `RUN`, `RUN:`; encode rejects data over 64 bytes.
- AC-2 `RegionLabels`: `eu-central-1` → `🇩🇪 Frankfurt`; unknown country code → `🌐`; unknown
  region → id as city; null maps don't fail.
- AC-3 Home greeting uses first name, falls back to `@username`, then to no name.
- AC-4 Home for a user without LIST_NODES and RUN_NODES shows `ui.home.no-access`, no region
  buttons, and the Refresh/Help row.
- AC-5 Home lists nodes with status emoji, host, region label and IP (`—` for nulls); shows
  `ui.home.no-nodes` when empty; uses `ui.home.all-nodes` when `allNodes`.
- AC-6 Home region buttons: sorted by city, 3 per row, callback `RUN:<id>`; absent without
  RUN_NODES; `ui.home.no-regions` when the list is empty.
- AC-7 Every screen's number of `%s` in the template equals the number of params, and no param is null.
- AC-8 Launch/help/error screens match §4.2–4.3 (keys, params, buttons, links).
- AC-9 `HomeController`: non-root lists own nodes; root lists all (`listTasks(null)`); null
  username → no service calls; no LIST/RUN → no `listTasks`/`getSupportedRegionIds` calls;
  `showHome` sends, `refreshHome`/`showHelp` edit.
- AC-10 `LaunchController` follows §5.3 for each branch: not allowed, region unavailable,
  runNode throws, HEALTHY+info, HEALTHY+no info, UNKNOWN, UNHEALTHY, checkNodeStatus throws;
  it edits `starting` before `runNode` and `waiting` before `checkNodeStatus`.
- AC-11 `UiRouter.canHandle`: true for callback, `/start`, `/start xyz`, `/menu`, plain text,
  null text; false for `/help`, `/runNodeIn Frankfurt`, `/listRunningNodes`.
- AC-12 `UiRouter.handle`: answers callback before dispatching; routes HOME/HELP/RUN/undecodable;
  message → showHome; controller exception → generic error sent, nothing thrown.
- AC-13 `TelegramUiMessenger`: HTML parse mode, escaped params passed to the translator,
  translated button labels, callback vs url buttons, no markup for an empty keyboard, "message is
  not modified" ignored on edit, answerCallback failures swallowed.
- AC-14 `TgRequestContext.initContext` works for callback query updates.
- AC-15 Every `ui.*` key exists in both `src/main/resources/messages.properties` and
  `messages_ru.properties`, the files contain no non-ASCII characters, `ui.*` values contain no
  `%`, and every `${ui.…}` key used in `src/main/java/org/github/akarkin1/ui` exists in both files.

## 8. Definition of done

- `mvn -B verify` passes; the existing 16 tests are unchanged and pass.
- `git diff --stat main` shows only files listed in §9 (plus `CLAUDE.md` and this spec).
- Every AC has a test. No TODOs, no commented-out code, no unused code.

## 9. Tasks and file ownership

| Task | Owner | Files |
|---|---|---|
| T1 Foundation | agent | all new `ui/**` main files as compilable stubs (records complete, logic methods `throw new UnsupportedOperationException()`), `Links` complete; fully implemented: `TailscaleNodeService` + `TailscaleEcsNodeService` (§5.8), `YamlApplicationConfiguration`, `application.yml`, both `messages*.properties` (§4.5) |
| T2 Tests | agent | `src/test/java/org/github/akarkin1/ui/**`, `src/test/java/org/github/akarkin1/tg/TgRequestContextTest.java` (written from this spec only) |
| T3 Screens | agent | `ui/UiAction`, `ui/UiContext`, `ui/screen/**` |
| T4 Integration | agent | `ui/UiRouter`, `ui/UiConfigurer`, `ui/controller/**`, `ui/messenger/**`, `tg/TgRequestContext`, `TailscaleVpnLambdaHandler`, `.github/workflows/deploy-tgbot-lambda.yml` |

T2–T4 run in parallel after T1. The tech lead merges, runs the suite, reviews and sends fixes back.

## 10. Manual test checklist (after deploy)

1. New chat → Start → home with greeting, nodes section, region buttons.
2. Type "hi" → home. Send a sticker → home.
3. Tap Help → help text with two working links → Menu → home.
4. Tap a region → message shows starting → waiting → node card (IP, auto-stop note, links).
5. Menu on the card → home lists the new node; Refresh twice → no error.
6. Read-only user → nodes listed, no region buttons. Unknown user → no-access text.
7. Telegram in Russian → all UI text in Russian.
8. `/help`, `/runNodeIn <city>`, `/listRunningNodes`, admin commands → unchanged behaviour.
9. ☰ Menu button lists `/menu` and `/help`.

## 11. Decision log

- (empty)
