#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=verify-common.sh
source "$script_dir/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

"$script_dir/verify.sh" -Workers 1 -Negative

run_case() {
  local task="$1" required="$2" forbidden="$3"
  local log="build/evidence/${task}.log"
  set +e
  "$gradlew" ":verification:${task}" -Pworkers=1 --rerun-tasks --console=plain >"$log" 2>&1
  local code=$?
  set -e
  if [[ "$code" -eq 0 ]] || ! grep -Eq "$required" "$log" || grep -Eq "$forbidden" "$log"; then
    echo "Failure contract failed: $task" >&2
    exit 1
  fi
  if [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
    echo "Shared infrastructure did not close: $task" >&2
    exit 1
  fi
  echo "PASS: $task rejected before forbidden body"
}

run_case boundaryTimeoutTest 'Boundary hook timed out: QUIESCE' 'NEGATIVE_BODY CleanupBTest'
run_case guardFailureTest '@Execution\(CONCURRENT\) is incompatible' 'FORBIDDEN_GUARD_BODY_EXECUTED'
run_case missingListenerTest 'Required listener missing' 'FORBIDDEN_MISSING_LISTENER_BODY'
run_case connectionGuardTest 'DataSource bypasses worker database' 'FORBIDDEN_CONNECTION_BODY|FORBIDDEN_SQL_INITIALIZER'
run_case initializationFailureTest 'INJECTED_INITIALIZATION_FAILURE' 'FORBIDDEN_INITIALIZATION_BODY'
run_case nestedGuardTest '@Nested test classes are not supported' 'FORBIDDEN_NESTED_BODY'
run_case contextHierarchyGuardTest '@ContextHierarchy is not supported' 'FORBIDDEN_CONTEXT_HIERARCHY_BODY'
run_case lazyConnectionGuardTest 'DataSource bypasses worker database' 'FORBIDDEN_LAZY_CONNECTION_BODY'

set +e
"$gradlew" :verification:invalidSettingsTest --console=plain >build/evidence/invalidSettingsTest.log 2>&1
code=$?
set -e
if [[ "$code" -eq 0 ]] || ! grep -q 'Incompatible test setting' build/evidence/invalidSettingsTest.log \
  || grep -q 'EVIDENCE start' build/evidence/invalidSettingsTest.log; then
  echo 'Invalid settings were not rejected before execution' >&2
  exit 1
fi
echo 'PASS: conflicting execution configuration rejected before worker launch'
