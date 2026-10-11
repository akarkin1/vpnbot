#!/usr/bin/env bash
# One-off migration (Phase 4): USER records keyed by a username (pre-registered users and the records
# created before Phase 4) become PENDING_USER records keyed by that username, with the same
# permissions. USER records keyed by a user id (all digits) are left alone. Idempotent.
# Needs the AWS CLI and jq. Usage: scripts/migrate-pending-users.sh [--dry-run]
# Deprecated: Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no
# longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
set -euo pipefail

CONFIG_TABLE_NAME=${CONFIG_TABLE_NAME:-vpnbot}
CONFIG_TABLE_REGION=${CONFIG_TABLE_REGION:-eu-central-1}

dry_run=false
case "${1:-}" in
  "") ;;
  --dry-run) dry_run=true ;;
  *) echo "Usage: $0 [--dry-run]" >&2; exit 2 ;;
esac

[ "$dry_run" = true ] && echo "DRY RUN – nothing is written"
echo "Table: $CONFIG_TABLE_NAME ($CONFIG_TABLE_REGION)"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# All USER records (the table is tiny, but follow the pagination anyway)
: > "$work/items.jsonl"
start_key=""
while :; do
  if [ -n "$start_key" ]; then
    aws dynamodb query --table-name "$CONFIG_TABLE_NAME" --region "$CONFIG_TABLE_REGION" \
      --key-condition-expression 'pk = :pk' --expression-attribute-values '{":pk":{"S":"USER"}}' \
      --exclusive-start-key "$start_key" --output json < /dev/null > "$work/page.json"
  else
    aws dynamodb query --table-name "$CONFIG_TABLE_NAME" --region "$CONFIG_TABLE_REGION" \
      --key-condition-expression 'pk = :pk' --expression-attribute-values '{":pk":{"S":"USER"}}' \
      --output json < /dev/null > "$work/page.json"
  fi
  jq -c '.Items[]' "$work/page.json" >> "$work/items.jsonl"
  start_key=$(jq -c '.LastEvaluatedKey // empty' "$work/page.json")
  [ -z "$start_key" ] && break
done

moved=0
while read -r item; do
  sk=$(echo "$item" | jq -r '.sk.S')
  if [[ "$sk" =~ ^[0-9]+$ ]]; then
    echo "keep  USER/$sk (keyed by id)"
    continue
  fi
  pending=$(echo "$item" | jq -c '{pk: {S: "PENDING_USER"}, sk: .sk, permissions: .permissions}')
  echo "move  USER/$sk -> PENDING_USER/$sk $(echo "$pending" | jq -c '.permissions')"
  if [ "$dry_run" = false ]; then
    aws dynamodb put-item --table-name "$CONFIG_TABLE_NAME" --region "$CONFIG_TABLE_REGION" \
      --item "$pending" < /dev/null
    aws dynamodb delete-item --table-name "$CONFIG_TABLE_NAME" --region "$CONFIG_TABLE_REGION" \
      --key "$(echo "$item" | jq -c '{pk: .pk, sk: .sk}')" < /dev/null
  fi
  moved=$((moved + 1))
done < "$work/items.jsonl"

echo "Done: $moved record(s) moved."
