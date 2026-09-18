#!/usr/bin/env bash
set -euo pipefail
Workers=2
while [[ $# -gt 0 ]]; do
  case "$1" in
    -Workers|--workers) Workers="$2"; shift 2 ;;
    *) echo "Usage: $0 [-Workers N]" >&2; exit 1 ;;
  esac
done

# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

log="build/evidence/mysql-workers-${Workers}.log"
guardLog="build/evidence/mysql-connectionGuardTest.log"
set +e
"$gradlew" :runtime:test \
  :verification:test --tests 'example.FlywaySmokeTest' --tests 'example.MysqlStorageSmokeTest' \
  -PjdbcBackend=mysql "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Gradle failed; inspect $log" >&2
  exit 1
fi
set +e
"$gradlew" :verification:connectionGuardTest -PjdbcBackend=mysql -Pworkers=1 --rerun-tasks --console=plain >"$guardLog" 2>&1
guardCode=$?
set -e

if [[ "$(match_count 'PTK infrastructure-start ' "$log")" -ne 1 ]] \
  || [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
  echo 'Invalid shared infrastructure lifecycle' >&2
  exit 1
fi
if ! grep -q 'jdbc\.backend=mysql' "$log"; then
  echo 'MySQL backend was not used' >&2
  exit 1
fi
if [[ "$guardCode" -eq 0 ]] || ! grep -q 'DataSource bypasses worker database' "$guardLog" \
  || grep -q 'FORBIDDEN_CONNECTION_BODY' "$guardLog"; then
  echo "Connection guard contract failed; inspect $guardLog" >&2
  exit 1
fi
echo "PASS: MySQL jdbcBackend workers=$Workers; flyway smoke, storage reset, connection guard."
exit 0
