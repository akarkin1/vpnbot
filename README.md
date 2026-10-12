# vpnbot

A Telegram bot that starts personal [Tailscale](https://tailscale.com/) exit nodes in AWS, in the
region you pick, and stops them when nobody is connected. Made for a handful of friends; everything
runs serverless, so an idle installation costs about a dollar a month.

## How it works

- You open the bot, tap a city, and a Fargate task starts there with Tailscale inside. A minute or
  two later the bot edits its message into a node card: hostname, public IP, how to use it as an
  exit node in the Tailscale app.
- The node agent (Python, `docker/node_agent`) joins your tailnet, watches for connected devices,
  warns before stopping and stops itself after 10 minutes with none. It updates the same card.
- Access is per Telegram user id: a new user taps *Request access*, every admin gets a message with
  role checkboxes, and the user is told the outcome. Roles: node admin (run nodes), read-only,
  user admin. A single root user (you) can do everything.
- Regions that are not set up yet can be requested from the menu; the owner deploys the region with
  a workflow and taps *Deployed, notify*.
- The bot itself is a Java 21 Lambda (SnapStart) behind an API Gateway HTTP API, with its runtime
  configuration in one DynamoDB table. See `CLAUDE.md` for the architecture and `docs/specs/` for
  the design of each phase.

## Minimal stack

| Piece | What | Cost |
|---|---|---|
| Lambda `vpnbot` + HTTP API | the bot, alias `live` with SnapStart | free tier for this traffic |
| DynamoDB table `vpnbot` | regions, users, access/region requests, update locks (on-demand) | cents |
| Secrets Manager | bot token, webhook secret, Tailscale auth key | ≈ $1.20 / month |
| S3 bucket | the Lambda jar | cents |
| Per region: VPC + public subnet, ECS cluster, Fargate task definition (0.5 vCPU / 1 GB), ECR repo, log group | the node infrastructure | ≈ $0.03–0.04 per node-hour (Fargate + public IPv4), ECR storage cents |

No NAT, no EC2 instances, nothing running while no node is up.

## Set it up from scratch

1. **Telegram**: create a bot with @BotFather, keep the token. Find your user id (e.g. @userinfobot).
2. **Tailscale**: create a reusable, ephemeral auth key for the nodes (tag them if you use ACLs).
3. **AWS**: an account with an IAM user for GitHub Actions (CloudFormation, Lambda, API Gateway,
   DynamoDB, ECS, ECR, EC2, Secrets Manager, S3, IAM role creation), and an S3 bucket for the jar.
4. **GitHub repository secrets**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `TG_BOT_TOKEN`,
   `TG_SECRET_TOKEN` (any random string: the webhook secret), `TAILSCALE_AUTH_TOKEN`.
5. **Repository variables**: `CONFIG_BASE_REGION` (home region, e.g. `eu-central-1`),
   `LAMBDA_JAR_BUCKET`, `ECS_STACK_NAME`, `TG_BOT_USERNAME`, `TG_ROOT_USERNAME`, `TG_ROOT_USER_ID`,
   `TG_SECRET_TOKEN_ID`, `TG_BOT_TOKEN_SECRET_ID`, `TAILSCALE_AUTH_TOKEN_SECRET_ID` (the three
   `*_SECRET_ID`s are the names the secrets get in Secrets Manager). Optional: `METRICS_ENABLED`,
   `CONFIG_CACHE_ENABLED`, `CONFIG_CACHE_TTL_SEC`.
6. **Deploy the bot**: run *VPN Bot Lambda CI-CD* (builds and uploads the jar), then *Deploy VPN
   Configurer Lambda Resources* (the stack, the secrets, the alias, your root user record, the
   Telegram command menu).
7. **Webhook**: take the HTTP API's invoke URL from the API Gateway console and register it:
   `https://api.telegram.org/bot<token>/setWebhook?url=<invoke-url>&secret_token=<TG_SECRET_TOKEN>`.
8. **Deploy a region**: run *Deploy Tailscale ECS Resources* with the region, Docker on. The region
   appears on the bot's home card when the workflow has finished. Repeat per region; *Delete ECS VPN
   Resources* removes one.
9. Open the bot, tap *Menu*. Invite your friends' Tailscale accounts to your tailnet; they request
   access in the bot.

## Release

A push to `main` deploys the bot code (jar and alias). A push to a `release/<version>` branch, e.g.
`release/2026.10` with the pom set to that version, runs the *Release* workflow: the code, the Lambda
stack (IAM, environment, Telegram menu) and every registered region (ECS stack and node image) at that
version. Tag the release branch (`v2026.10`) and let `main` continue as the next patch snapshot. The
manual workflows stay for one-off deploys.

## Development

`mvn -B verify` builds and tests the bot; the node agent tests run with
`cd docker/node_agent && PYTHONPATH=src python3 -m unittest discover -s tests -t .`. Pull requests
run both plus the shell script tests. Conventions and the architecture map are in `CLAUDE.md`,
planned work in `docs/roadmap.md`.
