# Roadmap

Living plan for the bot. Each phase gets its own spec in `docs/specs/` and its own
`feature/<name>` branch + PR. Decisions that shaped the plan are listed at the end.

## Done

- **Phase 1 – button UI** (`docs/specs/ui-ux-phase1.md`, PR #17): home message with region buttons,
  one-tap launch with a single progress message, help/error screens, EN/RU texts.

## Phase 2a – node reports itself, speed, metrics (done, merged)

Spec: `docs/specs/node-lifecycle-2a.md` · Branch: `feature/node-lifecycle-2a`

1. **Request metrics** via CloudWatch Embedded Metric Format: one structured stdout line per request
   (`TotalMs`, `S3Ms`, `EcsMs`, `TelegramMs`, dimension `UpdateKind`, namespace `vpnbot`).
   Feature flag `METRICS_ENABLED` (default `true`); off = nothing measured or printed.
2. **Node agent in Python** (`docker/node_agent`, replaces `start_tailscale.sh` and
   `monitor_connections.sh`): `amazonlinux:2023`, `boto3` + `requests` (pinned), no `aws` CLI.
   Starts Tailscale, detects idleness via `tailscale status --json`, and edits/sends Telegram messages
   itself: ready card, silent "stops in 2 minutes" warning, "stopped" message (+ card marked stopped;
   changed by "Stop updates in place").
   Texts are rendered by the Lambda (user's language) and passed as `TG_*` env vars.
3. **Launch flow**: the Lambda returns right after `RunTask` (no more health polling);
   `/runNodeIn` uses the same flow.
4. **Speed**: API Gateway integration timeout 0.6 s → 29 s; regions queried in parallel;
   TTL cache (`CONFIG_CACHE_TTL_SEC`, default 300 s, 0 = off) for the region list and per-region
   runtime parameters; webhook secret cached with the same TTL.
5. **Secrets**: bot token stored in Secrets Manager (`vpn-tg-bot-token`), created by the deploy
   workflows if missing, read by the node agent and by the Lambda (once at start-up, falling back to
   the `BOT_TOKEN` env var for one release).

## Phase 2b – Stop and reuse (done, merged)

Spec: `docs/specs/node-lifecycle-2b.md` · Branch: `feature/node-lifecycle-2b`

1. **🛑 Stop** button: own nodes in one tap; root can stop anyone's node after a confirmation and the
   owner is notified. Ownership is checked server-side against the task's `RunBy` tag. Needs
   `TaskInfo.runBy` and `EcsManager.stopTask` (`ecs:StopTask` is already allowed).
2. **Use existing or start another**: tapping a region where the user already has a node shows
   `[📋 Use <name>] [🚀 Start another] [🏠 Menu]`. Running several nodes stays allowed.

## Stop updates in place (done, merged)

Spec: `docs/specs/stop-in-place.md` · Branch: `feature/stop-in-place` (based on 2b)

User feedback: a stopped node edits its own message into a stopped card with
`[🚀 Start again] [🏠 Menu]` instead of sending a new message; the silent idle warning is deleted
when the node stops or a device connects again.

## Phase 3 – Config in DynamoDB, no Lambda VPC, SnapStart (spec ready)

Spec: `docs/specs/infra-cleanup-phase3.md` · Branch: `feature/infra-cleanup`

Goal: remove the Lambda's VPC, NAT instance and EFS, which only exist for the Lambda
(the Tailscale tasks use their own per-region VPC with an internet gateway), and cut cold starts.
Saves ≈ $13.2/month (t3.micro NAT $8.76, public IPv4 $3.65, EBS ≈ $0.76), removes an internet-facing
EC2 instance that needs AMI patching.

1. **Deploy A – DynamoDB**: one table `vpnbot` (`pk` = `REGION` / `USER` / `TG_UPDATE_LOCK`, `sk` = id)
   replaces `supported-regions.txt`, the per-region stack-output files, `user-permissions.json` and the
   EFS deduplication registry (atomic conditional put, 24 h TTL). Workflows write regions/users with
   `put-item`/`delete-item` (no S3 config any more); one-off local migration script. The in-memory
   config cache stays behind `CONFIG_CACHE_ENABLED` (default off) until measured.
2. **Deploy B – No VPC**: Lambda out of the VPC; delete VPC, subnets, gateway, route tables, NAT
   instance + ENI + EIP, security groups, EFS; drop S3 from the Lambda; remove `BOT_TOKEN`;
   Lambda timeout 30 s; node log retention 7 days.
3. **Deploy C – SnapStart**: published versions + alias `live`, API Gateway → alias, workflows publish
   a version on every code/config deploy (free for Java).

## Small follow-ups (from the 2026-10-09 prod validation)

- Node image runs Python 3.9, which boto3 no longer supports (deprecation warning at start-up):
  move to `python3.11` from the Amazon Linux 2023 repos and re-pin `docker/requirements.txt`.
- Cold start: Init ≈ 3.7 s + ≈ 0.75 s first-invocation work, warm requests ≈ 0.3 s. SnapStart is in
  Phase 3; more memory (CPU scales with memory; 1024 MB today) is the other lever if needed.
- Clean up the stale per-region stack-output files and the legacy `vpntgbot-s3` bucket (manual, once).

## Later

- **Phase 4 – access & admin**: "Request access" flow for unknown users (admin gets role buttons that
  call the existing `assignRolesToUser`; `ADMIN_CHAT_ID` setting); `/users` list with 🗑 buttons;
  optionally key permissions by Telegram user id instead of username.
- **Phase 5 – region requests**: "🌍 Other region…" lists regions enabled in the account
  (`ec2:DescribeRegions`) that are not set up yet; tapping one sends the admin a request with
  `[✅ Deployed, notify] [✖ Decline]`.
- **Extras**: deep links (`t.me/<bot>?start=run_<region>`), custom node names from the UI,
  "still running" reminder every N hours (no automatic stop – long downloads are legitimate).

## Decisions and findings

- No maximum node lifetime: overnight downloads are a valid use case.
- Running several nodes in the same region stays allowed (2b offers a choice, never a block).
- SnapStart is not compatible with EFS; deferred to Phase 3.
- The Lambda stack's NAT instance only serves the Lambda (its private subnet); VPN nodes egress via
  their own public IP and internet gateway, and Tailscale userspace networking needs no NAT.
- Metrics use EMF (no API calls); `PutMetricData` and X-Ray were rejected (latency/complexity).
- The node agent uses `requests` directly instead of a Telegram library (only two API calls);
  reconsider if it grows.
- Region-list cache: TTL (default 5 min) instead of no cache; staleness of a few minutes is acceptable.
- Prod metrics (2026-10-09): warm button taps 250–400 ms (ECS calls dominate), taps that start/stop a
  node 1.1–2.1 s, S3 is cached as intended; Fargate needs ≈ 20 s from RunTask to container start,
  Tailscale is up 1–2 s later.
- HTTP API deployments are snapshots: CloudFormation does not redeploy them on change (see 2a D-14).
