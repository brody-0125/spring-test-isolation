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

m2_repository() {
  if [[ -n "${M2_HOME:-}" ]]; then
    echo "$M2_HOME/repository"
  else
    echo "${HOME}/.m2/repository"
  fi
}
