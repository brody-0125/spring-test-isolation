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

log="build/evidence/config-cache-${Workers}.log"
set +e
clear_cached_runtime_artifact "$RepoRoot"
"$gradlew" :descriptor:clean :descriptor:publishToMavenLocal :runtime:clean :runtime:publishToMavenLocal :runtime:test :verification:test "-Pworkers=$Workers" --configuration-cache --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Configuration cache build failed; inspect $log" >&2
  exit 1
fi
if ! grep -q 'Configuration cache entry stored' "$log"; then
  echo "Configuration cache was not stored; inspect $log" >&2
  exit 1
fi
if [[ "$(match_count 'PTK infrastructure-start ' "$log")" -ne 1 ]] \
  || [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
  echo 'Invalid shared infrastructure lifecycle under configuration cache' >&2
  exit 1
fi
echo "PASS: configuration cache stored; workers=$Workers infrastructure lifecycle valid."
