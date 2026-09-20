#!/usr/bin/env bash
# Shared helpers for verification scripts (sourced, not executed directly).
set -euo pipefail

_verify_common_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

get_repo_root() {
  (cd "$_verify_common_dir/.." && pwd)
}

resolve_gradlew() {
  local root="${1:-$(get_repo_root)}"
  echo "$root/gradlew"
}

# Count non-overlapping regex matches in a file (ERE; use [0-9] not \d for portability).
match_count() {
  local pattern="$1"
  local file="$2"
  local n
  n=$(grep -Eo "$pattern" "$file" 2>/dev/null | wc -l | tr -d '[:space:]')
  echo "${n:-0}"
}

clear_cached_runtime_artifact() {
  local root="${1:-$(get_repo_root)}"
  local version gradle_home
  version="$(grep '^version=' "$root/gradle.properties" | cut -d= -f2)"
  rm -rf "$(m2_repository)/io/github/brody-0125/spring-test-isolation-runtime/$version"
  gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  rm -rf "$gradle_home/caches/modules-2/files-2.1/io.github.brody-0125/spring-test-isolation-runtime"
}

publish_runtime_to_maven_local() {
  local root="${1:-$(get_repo_root)}"
  local gradlew
  gradlew="$(resolve_gradlew "$root")"
  clear_cached_runtime_artifact "$root"
  (cd "$root" && "$gradlew" :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain -q)
}

m2_repository() {
  if [[ -n "${M2_HOME:-}" ]]; then
    echo "$M2_HOME/repository"
  else
    echo "${HOME}/.m2/repository"
  fi
}
