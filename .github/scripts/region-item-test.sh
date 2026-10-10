#!/usr/bin/env bash
# Tests for region-item.sh. Run: .github/scripts/region-item-test.sh
set -uo pipefail

script="$(dirname "$0")/region-item.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

check() {  # <description> <condition exit code>
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; else echo "FAIL - $1"; failures=$((failures + 1)); fi
}

cat > "$work/outputs.json" <<'JSON'
[
  {"OutputKey": "EcsClusterName", "OutputValue": "tailscale-node-ecs-cluster"},
  {"OutputKey": "EcsTaskDefinitionArn", "OutputValue": "arn:aws:ecs:eu-central-1:123:task-definition/tailscale:7"},
  {"OutputKey": "SubnetId", "OutputValue": "subnet-1"},
  {"OutputKey": "SecurityGroupId", "OutputValue": "sg-1"},
  {"OutputKey": "Unrelated", "OutputValue": "x"}
]
JSON

item=$("$script" eu-central-1 "$work/outputs.json")
check "valid outputs: exit 0" $?
expected='{"pk":{"S":"REGION"},"sk":{"S":"eu-central-1"},"ecsClusterName":{"S":"tailscale-node-ecs-cluster"},"ecsTaskDefinitionArn":{"S":"arn:aws:ecs:eu-central-1:123:task-definition/tailscale:7"},"subnetId":{"S":"subnet-1"},"securityGroupId":{"S":"sg-1"}}'
[ "$(echo "$item" | jq -c 'del(.updatedAt)')" = "$expected" ]
check "valid outputs: item has the region attributes" $?
echo "$item" | jq -e '.updatedAt.S | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")' >/dev/null
check "valid outputs: updatedAt is an ISO-8601 UTC timestamp" $?

for key in EcsClusterName EcsTaskDefinitionArn SubnetId SecurityGroupId; do
  jq --arg key "$key" 'map(select(.OutputKey != $key))' "$work/outputs.json" > "$work/missing.json"
  out=$("$script" eu-central-1 "$work/missing.json" 2>/dev/null); rc=$?
  [ $rc -ne 0 ] && [ -z "$out" ]
  check "missing $key: non-zero exit, no item" $?

  jq --arg key "$key" 'map(if .OutputKey == $key then .OutputValue = "" else . end)' "$work/outputs.json" > "$work/empty.json"
  out=$("$script" eu-central-1 "$work/empty.json" 2>/dev/null); rc=$?
  [ $rc -ne 0 ] && [ -z "$out" ]
  check "empty $key: non-zero exit, no item" $?
done

out=$("$script" eu-central-1 2>/dev/null); rc=$?
[ $rc -ne 0 ] && [ -z "$out" ]
check "missing argument: non-zero exit" $?

[ $failures -eq 0 ] && echo "All tests passed" || { echo "$failures test(s) failed"; exit 1; }
