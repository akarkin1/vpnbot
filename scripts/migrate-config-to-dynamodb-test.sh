#!/usr/bin/env bash
# Tests for migrate-config-to-dynamodb.sh against a fake `aws`. Run: scripts/migrate-config-to-dynamodb-test.sh
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/migrate-config-to-dynamodb.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

check() {  # <description> <condition exit code>
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; else echo "FAIL - $1"; failures=$((failures + 1)); fi
}

# Fake aws: S3 objects from $FAKE/s3/<key>, stack outputs from $FAKE/stacks/<region>.json,
# put-item calls appended to $FAKE/put-items.log (one item per line).
mkdir -p "$work/bin" "$work/s3" "$work/stacks"
cat > "$work/bin/aws" <<'FAKE'
#!/usr/bin/env bash
arg() { local name=$1; shift; while [ $# -gt 0 ]; do [ "$1" = "$name" ] && { echo "$2"; return; }; shift; done; }
case "$1 $2" in
  "s3 cp")
    key=${3#s3://*/}
    [ -f "$FAKE/s3/$key" ] || { echo "NoSuchKey: $key" >&2; exit 1; }
    cat "$FAKE/s3/$key" ;;
  "cloudformation describe-stacks")
    region=$(arg --region "$@")
    [ -f "$FAKE/stacks/$region.json" ] || { echo "Stack does not exist in $region" >&2; exit 254; }
    cat "$FAKE/stacks/$region.json" ;;
  "dynamodb put-item")
    echo "$(arg --table-name "$@") $(arg --region "$@") $(arg --item "$@" | jq -c .)" >> "$FAKE/put-items.log" ;;
  *) echo "unexpected aws call: $*" >&2; exit 1 ;;
esac
FAKE
chmod +x "$work/bin/aws"
export PATH="$work/bin:$PATH" FAKE="$work"

stack_outputs() {  # <cluster>
  printf '[{"OutputKey":"EcsClusterName","OutputValue":"%s"},{"OutputKey":"EcsTaskDefinitionArn","OutputValue":"arn:td"},{"OutputKey":"SubnetId","OutputValue":"subnet-1"},{"OutputKey":"SecurityGroupId","OutputValue":"sg-1"}]' "$1"
}
mkdir -p "$work/s3/ecs-tailscale-node/config"
printf 'eu-central-1\r\n\nus-east-1\n' > "$work/s3/ecs-tailscale-node/config/supported-regions.txt"
echo '{"alice":["ROOT_ACCESS"],"bob":["RUN_NODES","LIST_NODES","RUN_NODES"],"carol":[]}' \
  > "$work/s3/ecs-tailscale-node/config/user-permissions.json"
stack_outputs cluster-euc1 > "$work/stacks/eu-central-1.json"
stack_outputs cluster-use1 > "$work/stacks/us-east-1.json"

# Dry run: prints the items, writes nothing
out=$("$script" --dry-run); rc=$?
check "dry run: exit 0" $rc
[ ! -f "$work/put-items.log" ]
check "dry run: no put-item" $?
[ "$(echo "$out" | grep -c '"pk":{"S":"REGION"}')" -eq 2 ] && [ "$(echo "$out" | grep -c '"pk":{"S":"USER"}')" -eq 2 ]
check "dry run: prints 2 region items and 2 user items" $?

# Real run
"$script" > /dev/null; rc=$?
check "run: exit 0" $rc
[ "$(wc -l < "$work/put-items.log")" -eq 4 ]
check "run: 4 put-item calls (2 regions, 2 users with permissions)" $?
! grep -qv '^vpnbot eu-central-1 ' "$work/put-items.log"
check "run: every put-item targets table vpnbot in eu-central-1" $?
grep -q '"sk":{"S":"eu-central-1"},"ecsClusterName":{"S":"cluster-euc1"}' "$work/put-items.log" \
  && grep -q '"sk":{"S":"us-east-1"},"ecsClusterName":{"S":"cluster-use1"}' "$work/put-items.log"
check "run: region items use each region's stack outputs (CRLF and blank lines ignored)" $?
grep -q '{"pk":{"S":"USER"},"sk":{"S":"alice"},"permissions":{"SS":\["ROOT_ACCESS"\]}}' "$work/put-items.log" \
  && grep -q '{"pk":{"S":"USER"},"sk":{"S":"bob"},"permissions":{"SS":\["LIST_NODES","RUN_NODES"\]}}' "$work/put-items.log"
check "run: user items with unique permissions" $?
! grep -q '"carol"' "$work/put-items.log"
check "run: user without permissions is skipped" $?

# A listed region without a stack fails the run
rm "$work/put-items.log" "$work/stacks/us-east-1.json"
"$script" > /dev/null 2>&1; rc=$?
[ $rc -ne 0 ]
check "missing stack: non-zero exit" $?

"$script" --bogus > /dev/null 2>&1; rc=$?
[ $rc -eq 2 ]
check "unknown option: usage error" $?

[ $failures -eq 0 ] && echo "All tests passed" || { echo "$failures test(s) failed"; exit 1; }
