#!/usr/bin/env bash
# Tests for publish-live-version.sh against a fake `aws`. Run: .github/scripts/publish-live-version-test.sh
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/publish-live-version.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

check() {  # <description> <condition exit code>
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; else echo "FAIL - $1"; failures=$((failures + 1)); fi
}

mkdir -p "$work/bin"
cat > "$work/bin/aws" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE/calls.log"
case "$*" in
  "lambda publish-version"*) [ "${PUBLISH_FAILS:-}" = 1 ] && exit 254; echo "7" ;;
  "lambda wait"*|"lambda update-alias"*) ;;
  *) echo "unexpected aws call: $*" >&2; exit 1 ;;
esac
FAKE
chmod +x "$work/bin/aws"
export PATH="$work/bin:$PATH" FAKE="$work"

out=$("$script" vpnbot live); rc=$?
check "exit 0" $rc
expected="lambda wait function-updated-v2 --function-name vpnbot
lambda publish-version --function-name vpnbot --query Version --output text
lambda wait function-active-v2 --function-name vpnbot --qualifier 7
lambda update-alias --function-name vpnbot --name live --function-version 7"
[ "$(cat "$work/calls.log")" = "$expected" ]
check "waits for the update, publishes, waits for the version to be active, moves the alias" $?
echo "$out" | grep -q "Alias live -> version 7"
check "reports the published version" $?

rm "$work/calls.log"
PUBLISH_FAILS=1 "$script" vpnbot live > /dev/null 2>&1; rc=$?
[ $rc -ne 0 ] && ! grep -q "update-alias" "$work/calls.log"
check "a failed publish stops before touching the alias" $?

"$script" vpnbot > /dev/null 2>&1; rc=$?
[ $rc -eq 2 ]
check "missing argument: usage error" $?

[ $failures -eq 0 ] && echo "All tests passed" || { echo "$failures test(s) failed"; exit 1; }
