#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

log=build/evidence/multimodule.log
set +e
"$gradlew" :verification:test :verification-peer:test --parallel --max-workers=4 --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Multimodule tests failed; inspect $log" >&2
  exit 1
fi
if [[ "$(match_count 'PTK infrastructure-start ' "$log")" -ne 1 ]] \
  || [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
  echo 'Expected exactly one shared infrastructure lifecycle across both modules' >&2
  exit 1
fi

for module in verification verification-peer; do
  total=0
  shopt -s nullglob
  for file in "$module"/build/test-results/test/TEST-*.xml; do
    if grep -qE 'failures="[1-9]' "$file" || grep -qE 'errors="[1-9]' "$file"; then
      echo "Failure in $file" >&2
      exit 1
    fi
    tests=$(grep -oE 'tests="[0-9]+"' "$file" | head -1 | sed 's/tests="//;s/"//')
    total=$((total + tests))
  done
  shopt -u nullglob
  if [[ "$total" -ne 13 ]]; then
    echo "Expected thirteen tests for $module; got $total" >&2
    exit 1
  fi
done
echo 'PASS: both modules share one container pair; 26 tests passed.'
