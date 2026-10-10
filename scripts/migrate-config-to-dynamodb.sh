#!/usr/bin/env bash
# One-off migration (Phase 3, Deploy A): copies the bot's config from S3 into the DynamoDB table.
#   - every region of supported-regions.txt → REGION item, built from the region's ECS stack outputs
#   - every user of user-permissions.json with permissions → USER item
# Idempotent (put-item overwrites). Needs the AWS CLI and jq.
# Usage: scripts/migrate-config-to-dynamodb.sh [--dry-run]
set -euo pipefail

CONFIG_BUCKET=${CONFIG_BUCKET:-ecs-mgmt-tg-bot-euc1-s3}
CONFIG_DIR=${CONFIG_DIR:-ecs-tailscale-node/config}
REGIONS_FILE=${REGIONS_FILE:-supported-regions.txt}
USER_PERMISSIONS_FILE=${USER_PERMISSIONS_FILE:-user-permissions.json}
ECS_STACK_NAME=${ECS_STACK_NAME:-vpn-ecs-resources-cfn}
CONFIG_TABLE_NAME=${CONFIG_TABLE_NAME:-vpnbot}
CONFIG_TABLE_REGION=${CONFIG_TABLE_REGION:-eu-central-1}

dry_run=false
case "${1:-}" in
  "") ;;
  --dry-run) dry_run=true ;;
  *) echo "Usage: $0 [--dry-run]" >&2; exit 2 ;;
esac

region_item="$(cd "$(dirname "$0")" && pwd)/../.github/scripts/region-item.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

put_item() {
  echo "  $(echo "$1" | jq -c .)"
  if [ "$dry_run" = false ]; then
    aws dynamodb put-item --table-name "$CONFIG_TABLE_NAME" --region "$CONFIG_TABLE_REGION" \
      --item "$1" < /dev/null
  fi
}

[ "$dry_run" = true ] && echo "DRY RUN – nothing is written"
echo "Table: $CONFIG_TABLE_NAME ($CONFIG_TABLE_REGION)"

echo "Regions from s3://$CONFIG_BUCKET/$CONFIG_DIR/$REGIONS_FILE:"
aws s3 cp "s3://$CONFIG_BUCKET/$CONFIG_DIR/$REGIONS_FILE" - --region "$CONFIG_TABLE_REGION" \
  | tr -d '\r' > "$work/regions.txt"
while read -r region; do
  [ -z "$region" ] && continue
  aws cloudformation describe-stacks --stack-name "$ECS_STACK_NAME" --region "$region" \
    --query 'Stacks[0].Outputs' --output json < /dev/null > "$work/outputs.json"
  put_item "$("$region_item" "$region" "$work/outputs.json")"
done < "$work/regions.txt"

echo "Users from s3://$CONFIG_BUCKET/$CONFIG_DIR/$USER_PERMISSIONS_FILE:"
aws s3 cp "s3://$CONFIG_BUCKET/$CONFIG_DIR/$USER_PERMISSIONS_FILE" - --region "$CONFIG_TABLE_REGION" \
  | tr -d '\r' > "$work/users.json"
jq -c 'to_entries[] | select(.value | length > 0)
       | {pk: {S: "USER"}, sk: {S: .key}, permissions: {SS: (.value | unique)}}' "$work/users.json" \
  | tr -d '\r' > "$work/user-items.jsonl"
while read -r item; do
  put_item "$item"
done < "$work/user-items.jsonl"

echo "Done."
