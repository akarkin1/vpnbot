# Phase 5 – region requests, release 2026.10

Status: draft for review, 2026-10-12. Branch `feature/region-requests`, version `2026.10-SNAPSHOT`;
this is the last feature of release `2026.10` (§11).

## 1. Goal

A user who can run nodes can ask for a region that is not set up yet, from the bot:

1. The home card gets a `[🌍 Other region…]` button under the region buttons. It opens the
   **Regions screen**: the AWS regions enabled in the account that have no node infrastructure yet,
   as buttons; regions already requested are shown with ⏳.
2. Tapping a region records a **region request** and sends every root user a message naming the
   requester and the region, with `[✅ Deployed, notify]` and `[✖ Decline]`.
3. The owner deploys the region as today ("Deploy Tailscale ECS Resources" workflow), then taps
   **Deployed, notify**: the bot checks that the region is now supported, closes the request and
   tells the requester the region is available. **Decline** closes it and tells the requester.

Release 2026.10 follows (§11): version `2026.10`, tag, GitHub release, deploy, then `2026.10.1-SNAPSHOT`.

## 2. Non-goals

- Deploying the region from the bot (the workflow stays manual; the bot only asks and confirms).
- Several requesters per region: the first request wins, later users see "already requested"
  (D-3); no per-region subscriber list.
- Changing the roles/permissions model: no new permission (D-2).
- Region opt-in: regions that need opting in and are not opted in are not listed (D-4).
- Deep links, custom node names, reminders (roadmap extras); the node agent; text commands.
- New dependencies.

## 3. Ground rules

Same as `docs/specs/cleanup-2026-10.md` §3 and §4.7 (CLAUDE.md, constructor injection, wiring in
`BotDependencies`, exact contracts, file ownership, `SPEC DEVIATION: <what> / <why> / <proposed
option>` escalation). New code follows the access-request implementation
(`AccessController`/`AccessScreens`/`AccessRequestRecord`) as its pattern.

## 4. Behaviour

### 4.1 Home card

- `HomeModel` gains `boolean canRequestRegions` = the user may run nodes (`RUN_NODES` or root),
  i.e. the same condition as the region buttons (D-2).
- When true, one row `[🌍 ${ui.button.other-region}]` (`UiAction.regions()`) follows the region
  rows (after them, before the stop rows). It is shown even when no region is set up yet (the
  "no regions" text then gets a way out).

### 4.2 Regions screen (`REGIONS`)

`RegionController.show(context, messageId)`:

1. Caller may not run nodes → edit to `${ui.regions.not-allowed}` with `[🏠 Menu]`.
2. `available` = `regionCatalog.enabledRegions()` minus `nodeService.getSupportedRegionIds()`,
   sorted by city label case-insensitively (`RegionLabels.city`); `pending` = the region ids of the
   open requests (`regionRequests.findAll()`).
3. Empty `available` → `${ui.regions.none}` with `[🏠 Menu]`.
4. Else the screen `🌍 <b>${ui.regions.title}</b>\n${ui.regions.hint}` with one button per region,
   three per row like the home card: label `RegionLabels.label(regionId)`, prefixed with `⏳ ` when
   pending; callback `UiAction.regionRequest(regionId)`. Last row `[🏠 Menu]`.

A region the catalog returns but `application.yml` does not know gets the id as its label and the
globe flag (`RegionLabels` already does that).

### 4.3 Request (`REGION_REQ:<regionId>`)

`RegionController.request(context, messageId, regionId)`:

1. Caller may not run nodes → not-allowed screen.
2. Region already supported → `${ui.regions.already-available}` (with the region label) and the
   `[🏠 Menu]` row: the home card has the button now.
3. Region not in the catalog (not enabled, or a stale button) → `${ui.regions.unknown}` + Menu.
4. An open request exists for the region → `${ui.regions.already-requested}` + Menu (D-3).
5. Else create `RegionRequest(regionId, userId, username, firstName, lastName, languageCode,
   requestedAt)`, edit to `${ui.regions.requested}` (with the label) + Menu, and notify every root
   user (D-1) with `RegionScreens.adminRequest(request)`:
   `🌍 <b>First Last</b> (@username, id) ${ui.regions.asks} 🇩🇪 Frankfurt <code>eu-central-1</code>`
   (the region id in `<code>` so it is tap-to-copy for the deploy workflow, like the IP on the node
   card; label and id are params) and the buttons `[✅ ${ui.button.deployed-notify}]` (`UiAction.regionDeployed(regionId)`) and
   `[✖ ${ui.button.decline}]` (`UiAction.regionDecline(regionId)`). The requester line is the
   same `First Last (@username, id)` block as the access request (reuse it: move `Requester` into a
   shared helper in `ui.screen`, e.g. `UserLine`, used by both screens).
   Admin chats, languages and failure handling exactly as `AccessController.notifyAdmins` (D-4 of
   Phase 4): no root user → a warning in the log, the request still stands.

### 4.4 Decision (`REGION_DONE:<regionId>`, `REGION_DECLINE:<regionId>`)

Both: caller must be root (D-1), else `${ui.regions.not-allowed}`; no open request for the region →
`${ui.regions.already-handled}` (no buttons), done.

- **Deployed, notify**: if the region is *not* in `nodeService.getSupportedRegionIds()` → edit the
  admin message to `RegionScreens.notDeployedYet(request)`: the same text plus
  `⚠️ ${ui.regions.not-deployed-yet}` and the same two buttons, nothing else changes (the config
  cache may lag up to its TTL, the hint says so). If it is → delete the request, edit to the closed
  variant `✅ 🇩🇪 Frankfurt <code>eu-central-1</code> ${ui.regions.requested-by} <b>First Last</b>
  (@username, id): ${ui.regions.deployed-admin}` (no buttons; the closed variants put the region
  first and drop "asks for", D-10), notify the requester in their language with
  `✅ ${ui.regions.available}` (region label) and `[🏠 Menu]`.
- **Decline**: delete the request, edit to `✖ 🇩🇪 Frankfurt <code>eu-central-1</code>
  ${ui.regions.requested-by} <b>First Last</b> (@username, id): ${ui.regions.declined-admin}` (no
  buttons), notify the requester with `✖ ${ui.regions.declined}` (region label), no buttons.
- A failing requester notification is logged and does not fail the decision (as for access).
- The first decision wins: the second admin's tap finds no request → already handled.

### 4.5 Region catalog

`RegionCatalog.enabledRegions()`: the ids of the regions enabled for the account, from
`ec2:DescribeRegions` without `allRegions` (so opt-in regions appear only once opted in, D-4),
called in the Lambda's own region (`Settings.awsRegion`, env `AWS_REGION`, default
`eu-central-1`) through the existing `Ec2ClientPool`. `CachedRegionCatalog` caches the set for the
config cache TTL when the config cache is enabled (same decorator pattern as
`CachedTaskConfigService`). An EC2 failure propagates: the router's generic error path answers.
`SnapStartPrimer` gains the step `regionCatalog` (one `enabledRegions()` call, failures logged).

### 4.6 Expiry

A region request expires after 30 days (`expiresAt` TTL; D-5): deploying a region is not a
same-day decision. `find`/`findAll` ignore expired records like the access requests do.

### 4.7 Texts (EN / RU, RU as `\uXXXX`; no `%` in `ui.*` values)

| Key | EN | RU |
|---|---|---|
| `ui.button.other-region` | Other region… | Другой регион… |
| `ui.regions.title` | Request a region | Запросить регион |
| `ui.regions.hint` | Pick a region to ask the administrators to set it up. ⏳ marks regions already requested. | Выберите регион, чтобы попросить администраторов настроить его. ⏳ – регион уже запрошен. |
| `ui.regions.none` | Every available region is already set up. | Все доступные регионы уже настроены. |
| `ui.regions.not-allowed` | You can't request regions. | Вы не можете запрашивать регионы. |
| `ui.regions.requested` | Your request for %s was sent to the administrators. You'll get a message here once it's set up. | Ваш запрос на регион %s отправлен администраторам. Вы получите сообщение, когда он будет настроен. |
| `ui.regions.already-requested` | %s has already been requested. You'll see it in the menu once it's set up. | Регион %s уже запрошен. Он появится в меню, когда будет настроен. |
| `ui.regions.already-available` | %s is already available – see the menu. | Регион %s уже доступен – смотрите меню. |
| `ui.regions.unknown` | This region can't be set up. | Этот регион нельзя настроить. |
| `ui.regions.asks` | asks for a node region: | просит регион для ноды: |
| `ui.regions.not-deployed-yet` | The region isn't in the list of supported regions yet. Run the deploy workflow first; the list refreshes within a few minutes. | Регион ещё не в списке поддерживаемых. Сначала запустите деплой; список обновится в течение нескольких минут. |
| `ui.regions.requested-by` | requested by | запросил(а) |
| `ui.regions.deployed-admin` | deployed, the requester notified. | развёрнут, запросивший уведомлён. |
| `ui.regions.declined-admin` | declined. | отклонён. |
| `ui.regions.already-handled` | This request has already been handled. | Этот запрос уже обработан. |
| `ui.regions.available` | %s is now available – pick it in the menu. | Регион %s теперь доступен – выберите его в меню. |
| `ui.regions.declined` | Your request for %s was declined. | Ваш запрос на регион %s отклонён. |
| `ui.button.deployed-notify` | Deployed, notify | Развёрнуто, уведомить |

`%s` in the table marks where the region label goes; `ui.*` values hold no format specifier
(CLAUDE.md), so the implementer splits such a text into the key's sentence tail and a Java
template with the label as a bold, HTML-escaped param in front, e.g. `"✅ <b>%s</b>
${ui.regions.available}"` with `ui.regions.available = is now available – pick it in the menu.`
The RU texts are reworded to the same shape (label first), ASCII-escaped.

## 5. Data model (table `vpnbot`)

| Record | sk | Attributes |
|---|---|---|
| `REGION_REQUEST` (new) | region id (`eu-central-1`) | `userId` (N), `username` (S, nullable), `firstName` (S), `lastName` (S, nullable), `languageCode` (S), `requestedAt` (N, epoch ms), `expiresAt` (N, TTL = requestedAt + 30 days) |

Bean `dynamodb.RegionRequestRecord` (`@DynamoDbBean`, Lombok getters with the key annotations, as
`AccessRequestRecord`); `ConfigTables` gains `regionRequests()`.

## 6. Infra

- `cloudformation/vpn-configurer-lambda.yml`: add `ec2:DescribeRegions` to the Lambda's EC2
  statement (resource `*`, as `DescribeNetworkInterfaces`). A stack deploy is part of the release.
- `Settings.awsRegion` from `AWS_REGION` (set by Lambda), default `eu-central-1`.

## 7. Contracts

```java
// ec2
public interface RegionCatalog { Set<String> enabledRegions(); }
public class Ec2RegionCatalog implements RegionCatalog { Ec2RegionCatalog(Ec2ClientPool pool, String regionId, RequestMetrics metrics); }   // DescribeRegions timed under MetricComponent.ECS like the other AWS calls (D-6)
public class CachedRegionCatalog implements RegionCatalog { CachedRegionCatalog(RegionCatalog delegate, Duration ttl, Clock clock); }
// region requests (package org.github.akarkin1.region)
public record RegionRequest(String regionId, long userId, String username, String firstName, String lastName, String languageCode, long requestedAt) { public TgUser user(); }
public interface RegionRequestService { Optional<RegionRequest> find(String regionId); List<RegionRequest> findAll(); void create(RegionRequest r); void delete(String regionId); }
public class DynamoDbRegionRequestService implements RegionRequestService { (DynamoDbTable<RegionRequestRecord> table, Clock clock, RequestMetrics metrics) }
// dynamodb
public class RegionRequestRecord { TYPE = "REGION_REQUEST"; type/pk, regionId/sk, userId (Long), username, firstName, lastName, languageCode, requestedAt, expiresAt }
public record ConfigTables(regions, users, pendingUsers, updateLocks, accessRequests, regionRequests)
// config
Settings + String awsRegion
// ui
UiAction: record Regions() {}  // REGIONS
          record RegionRequestAction(String regionId) {}   // REGION_REQ:<regionId>   (name avoids the clash with region.RegionRequest)
          record RegionDeployed(String regionId) {}        // REGION_DONE:<regionId>
          record RegionDecline(String regionId) {}         // REGION_DECLINE:<regionId>
          + factories regions(), regionRequest(String), regionDeployed(String), regionDecline(String); regionId: not blank, no ':' or whitespace
public class RegionController { (RegionCatalog catalog, TailscaleNodeService nodeService, RegionRequestService regionRequests, PermissionsService permissionsService, Authorizer authorizer, UiMessenger messenger, RegionScreens screens, Clock clock)
  void show(UiContext, Integer messageId); void request(UiContext, Integer messageId, String regionId);
  void deployed(UiContext, Integer messageId, String regionId); void decline(UiContext, Integer messageId, String regionId); }
public class RegionScreens { (RegionLabels labels)
  Screen regions(List<String> available, Set<String> pending); Screen none(); Screen notAllowed();
  Screen requested(String regionId); Screen alreadyRequested(String regionId); Screen alreadyAvailable(String regionId); Screen unknown();
  Screen adminRequest(RegionRequest r); Screen notDeployedYet(RegionRequest r); Screen deployedAdmin(RegionRequest r); Screen declinedAdmin(RegionRequest r); Screen alreadyHandled();
  Screen available(String regionId); Screen declined(String regionId); }
public record HomeModel(…, boolean hasAccess, boolean canRequestRegions)
UiRouter(home, launch, node, access, users, region, messenger, errorScreen)
// wiring: BotDependencies builds the catalog (cached when configCacheEnabled), the request service, RegionScreens, RegionController; SnapStartPrimer(…, regionCatalog)
```

## 8. Acceptance criteria

- AC-1 Home: `canRequestRegions` ⇔ the user may run nodes; the `[🌍 Other region…]` row follows
  the region rows and is present with an empty region list; absent for a user who cannot run nodes.
- AC-2 `RegionController.show`: the four branches of §4.2; available = enabled − supported, sorted
  by city; pending regions get the ⏳ prefix; three buttons per row; Menu last.
- AC-3 `request`: the five branches of §4.3 in that order; the record carries the requester's
  context and `requestedAt` from the clock; every root user (and only root users, pending users
  excluded) gets `adminRequest`; a failing admin send is logged, the others still get it.
- AC-4 `deployed`: non-root → not allowed; no request → already handled; region not supported →
  `notDeployedYet` with the buttons and no write; supported → delete, `deployedAdmin`, requester
  notified in their language with the label; a failing requester send does not fail the decision.
  `decline`: delete, `declinedAdmin`, requester notified.
- AC-5 `RegionRequestRecord`: pk `REGION_REQUEST`, sk region id, attributes of §5, `expiresAt` =
  `requestedAt` + 30 days in seconds; nulls omitted; round trip. `DynamoDbRegionRequestService`:
  `find` reads by key and ignores expired records; `findAll` queries the pk and ignores expired;
  `create` writes the record; `delete` deletes by key; all calls inside `metrics.time`.
- AC-6 `Ec2RegionCatalog` maps `DescribeRegions` to the region names; `CachedRegionCatalog` serves
  from cache within the TTL and refreshes after it; `SnapStartPrimer` calls `enabledRegions()`
  once and logs a failure without throwing.
- AC-7 `UiAction`: the four new records round-trip; `REGION_REQ:eu-central-1`, `REGIONS`,
  `REGION_DONE:…`, `REGION_DECLINE:…`; invalid ids (blank, `a:b`, whitespace) → empty; the router
  dispatches them to `RegionController`.
- AC-8 Texts: every new key in both files, ASCII, no `%` in `ui.*` values, `UiMessagesTest` green;
  every screen's dynamic value is a param.
- AC-9 `BotDependenciesTest`: `router()` still builds; priming reaches the EC2 mock with a
  `DescribeRegions` call; `Settings.awsRegion` defaults to `eu-central-1`.
- AC-10 `mvn -B verify` green, the shell tests green, no project deprecation warning.

## 9. Definition of done

AC-1…AC-10; CLAUDE.md (architecture lines: `RegionController`, `REGION_REQUEST`, `RegionCatalog`)
and the roadmap updated; the §11 release done and the smoke test passed.

## 10. Tasks and file ownership

| Task | Files |
|---|---|
| T1 Catalog + requests (Java) | `ec2/RegionCatalog`, `ec2/Ec2RegionCatalog`, `ec2/CachedRegionCatalog`, `region/**` (new package), `dynamodb/RegionRequestRecord`, `dynamodb/ConfigTables`, `config/Settings` (+ `awsRegion`), `startup/SnapStartPrimer` |
| T2 T1 tests | `src/test/java/**` for T1 (`FakeTable`, `RecordingRequestMetrics`), `SettingsTest`, `SnapStartPrimerTest` |
| T3 UI (Java) | `ui/UiAction`, `ui/UiRouter`, `ui/controller/RegionController`, `ui/controller/HomeController`, `ui/screen/RegionScreens`, `HomeScreen`, `HomeModel`, `AccessScreens` (shared requester line), `BotDependencies` (wiring), messages |
| T4 T3 tests | `UiActionTest`, `UiRouterTest`, `RegionControllerTest`, `RegionScreensTest`, `HomeScreenTest`, `HomeControllerTest`, `AccessScreensTest`, `BotDependenciesTest`, `UiMessagesTest` |
| H1 Infra, docs, release (tech lead) | `cloudformation/vpn-configurer-lambda.yml`, `CLAUDE.md`, `docs/roadmap.md`, this spec, `pom.xml` (release) |

Order: T1/T2 → T3/T4 (T3 wires T1's classes) → H1. Each pair merged before the next starts.

## 11. Release 2026.10 and deploy checklist (owner)

1. Merge the Phase 5 PR into `main`.
2. Release commit on `main` (tech lead prepares a PR): `pom.xml` `2026.10`; roadmap and
   CLAUDE.md say "2026.10 released"; tag `v2026.10` (pushed by the owner, the session cannot push
   tags); GitHub release "vpnbot 2026.10" with the notes the tech lead drafts (UI phases 2a–4,
   DynamoDB config, SnapStart, no Lambda VPC, system commands, composition root, region requests).
3. Deploy: the merge of the release commit triggers "VPN Bot Lambda CI-CD" (jar
   `vpnbot-2026.10.jar`); then run "Deploy VPN Configurer Lambda Resources" by hand (the IAM change
   `ec2:DescribeRegions`, version `2026.10` from the pom).
4. Smoke test: home card shows `[🌍 Other region…]`; the Regions screen lists the enabled regions
   not set up; request one with a second account → root gets the message; tap Deployed before
   deploying → "not deployed yet"; run "Deploy Tailscale ECS Resources" for that region (or pick a
   region you really want); tap Deployed, notify → requester notified, region on the home card;
   Decline path with another region.
5. Next development commit on `main`: `pom.xml` `2026.10.1-SNAPSHOT` (D-7).

## 12. Decisions (review)

- D-1 Region requests go to **root users only** and only root can decide: deploying a region is
  the owner's infra action (GitHub Actions), not a user-admin task. `USER_MANAGEMENT` admins are
  not notified.
- D-2 No new permission: whoever may run nodes may request a region (`RUN_NODES` or root).
- D-3 One open request per region; later users see "already requested" and are not notified
  separately (they see the region appear on the home card). Keeps the record flat.
- D-4 The catalog lists regions enabled for the account (`DescribeRegions` without `allRegions`):
  an opt-in region that is not opted in cannot be deployed by the workflow anyway, and listing it
  would produce a request the owner has to decline.
- D-5 Region requests expire after 30 days (access requests: 7): the decision needs a deploy.
- D-6 `DescribeRegions` is timed under `MetricComponent.ECS` like the ECS and EC2 calls today (no
  new metric); it is one cached call per TTL.
- D-7 (owner) After the release, `main` moves to `2026.10.1-SNAPSHOT`: the next release is the
  patch of the current month until a release in a later month is cut, so the snapshot never names
  a month that may slip. A release in a later month sets `YYYY.MM` at release time (one line).
- D-8 The requester line of the admin message (`First Last (@username, id)`) is shared with the
  access request screen through one helper in `ui.screen`, so the two messages stay identical in
  form.
- D-9 (owner) A root user may request a region too and then receives their own request message:
  it works as a to-do card (deploy, tap Deployed, the bot confirms). The requester is not excluded
  from the admin list.
- D-10 (owner) The region id is rendered in `<code>` (tap-to-copy, like the node card's IP) in the
  admin message and its closed variants; the closed variants lead with the region and say
  "requested by", since "asks for" reads wrong once the request is decided.

## 13. Decision log

(filled during implementation: `SPEC DEVIATION`s and their resolutions)
