#!/usr/bin/env bash
set -euo pipefail
Workers=2
while [[ $# -gt 0 ]]; do
  case "$1" in
    -Workers|--workers) Workers="$2"; shift 2 ;;
    *) echo "Usage: $0 [-Workers 1|2|4]" >&2; exit 1 ;;
  esac
done
case "$Workers" in 1|2|4) ;; *)
  echo 'Workers must be 1, 2, or 4' >&2
  exit 1
esac

# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

log="build/evidence/spring-session-${Workers}.log"
set +e
"$gradlew" :verification:sessionTest "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Spring Session keyevent fixture failed; inspect $log" >&2
  exit 1
fi
if grep -q 'SESSION LEAK' "$log"; then
  echo 'Session index leakage detected' >&2
  exit 1
fi
for pattern in 'PTK infrastructure-start ' 'PTK infrastructure-closed'; do
  if [[ "$(match_count "$pattern" "$log")" -ne 1 ]]; then
    echo "Infrastructure lifecycle mismatch: $pattern" >&2
    exit 1
  fi
done
for pattern in 'SESSION verified ' 'SESSION audited ' 'SESSION indexed '; do
  if [[ "$(match_count "$pattern" "$log")" -ne 4 ]]; then
    echo "Expected four completed session classes: $pattern" >&2
    exit 1
  fi
done
if [[ "$(match_count "SESSION verified worker=[0-9]+ peers=${Workers}" "$log")" -ne 4 ]]; then
  echo 'Barrier did not prove the requested cross-worker concurrency' >&2
  exit 1
fi
namespace_count=$(grep -oE 'SESSION indexed worker=[0-9]+ namespace=[^ ]+' "$log" | sort -u | wc -l | tr -d ' ')
if [[ "$namespace_count" -ne "$Workers" ]]; then
  echo 'Session index namespaces must map one-to-one with workers' >&2
  exit 1
fi
count=0
shopt -s nullglob
for file in verification/build/test-results/sessionTest/TEST-*.xml; do
  if grep -qE 'failures="[1-9]' "$file" || grep -qE 'errors="[1-9]' "$file" || grep -qE 'skipped="[1-9]' "$file"; then
    echo "Failed or skipped test in $file" >&2
    exit 1
  fi
  tests=$(grep -oE 'tests="[0-9]+"' "$file" | head -1 | sed 's/tests="//;s/"//')
  count=$((count + tests))
done
shopt -u nullglob
if [[ "$count" -ne 4 ]]; then
  echo "Expected four successful tests, got $count" >&2
  exit 1
fi
echo "PASS: Spring Session keyevent ACL fixture workers=$Workers; indexed sessions, keyevents, cleanup audit."
