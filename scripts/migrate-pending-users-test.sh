#!/usr/bin/env bash
# Tests for migrate-pending-users.sh against a fake `aws`. Run: scripts/migrate-pending-users-test.sh
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/migrate-pending-users.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

check() {  # <description> <condition exit code>
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; else echo "FAIL - $1"; failures=$((failures + 1)); fi
}

# Fake aws: query returns $FAKE/page1.json, then $FAKE/page2.json when an exclusive start key is given;
# put-item / delete-item are appended to $FAKE/writes.log.
mkdir -p "$work/bin"
cat > "$work/bin/aws" <<'FAKE'
#!/usr/bin/env bash
case "$1 $2" in
  "dynamodb query")
    if printf '%s\n' "$@" | grep -q -- '--exclusive-start-key'; then cat "$FAKE/page2.json"; else cat "$FAKE/page1.json"; fi ;;
  "dynamodb put-item"|"dynamodb delete-item")
    sub=$2; arg=""; while [ $# -gt 0 ]; do case "$1" in --item|--key) arg=$2;; esac; shift; done
    echo "$sub $arg" >> "$FAKE/writes.log" ;;
  *) echo "unexpected aws call: $*" >&2; exit 1 ;;
esac
FAKE
chmod +x "$work/bin/aws"
export PATH="$work/bin:$PATH" FAKE="$work"

cat > "$work/page1.json" <<'JSON'
{"Items":[{"pk":{"S":"USER"},"sk":{"S":"alice"},"permissions":{"SS":["ROOT_ACCESS"]}},
          {"pk":{"S":"USER"},"sk":{"S":"123456789"},"username":{"S":"bob"},"permissions":{"SS":["RUN_NODES"]}}],
 "LastEvaluatedKey":{"pk":{"S":"USER"},"sk":{"S":"123456789"}}}
JSON
cat > "$work/page2.json" <<'JSON'
{"Items":[{"pk":{"S":"USER"},"sk":{"S":"Carol_99"},"permissions":{"SS":["LIST_NODES","SUPPORTED_REGIONS"]}}]}
JSON

out=$("$script" --dry-run); rc=$?
check "dry run: exit 0" $rc
[ ! -f "$work/writes.log" ]
check "dry run: writes nothing" $?
echo "$out" | grep -q "move  USER/alice -> PENDING_USER/alice" && echo "$out" | grep -q "move  USER/Carol_99 -> PENDING_USER/Carol_99" && echo "$out" | grep -q "keep  USER/123456789"
check "dry run: lists both pages, keeps the id-keyed record" $?
echo "$out" | grep -q "Done: 2 record(s) moved."
check "dry run: counts 2 moves" $?

"$script" > /dev/null; rc=$?
check "run: exit 0" $rc
expected='put-item {"pk":{"S":"PENDING_USER"},"sk":{"S":"alice"},"permissions":{"SS":["ROOT_ACCESS"]}}
delete-item {"pk":{"S":"USER"},"sk":{"S":"alice"}}
put-item {"pk":{"S":"PENDING_USER"},"sk":{"S":"Carol_99"},"permissions":{"SS":["LIST_NODES","SUPPORTED_REGIONS"]}}
delete-item {"pk":{"S":"USER"},"sk":{"S":"Carol_99"}}'
[ "$(cat "$work/writes.log")" = "$expected" ]
check "run: put PENDING_USER then delete USER, per username-keyed record, nothing for the id-keyed one" $?

# Re-run on an already migrated table is a no-op
echo '{"Items":[{"pk":{"S":"USER"},"sk":{"S":"123456789"},"username":{"S":"bob"},"permissions":{"SS":["RUN_NODES"]}}]}' > "$work/page1.json"
rm "$work/writes.log"
out=$("$script"); rc=$?
[ $rc -eq 0 ] && [ ! -f "$work/writes.log" ] && echo "$out" | grep -q "Done: 0 record(s) moved."
check "re-run: no-op" $?

"$script" --bogus > /dev/null 2>&1; rc=$?
[ $rc -eq 2 ]
check "unknown option: usage error" $?

[ $failures -eq 0 ] && echo "All tests passed" || { echo "$failures test(s) failed"; exit 1; }
