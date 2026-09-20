#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

log=build/evidence/publication-verify.log
set +e
"$gradlew" :runtime:clean :runtime:publishToMavenLocal --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Runtime publication failed; inspect $log" >&2
  exit 1
fi
set +e
"$gradlew" -p plugin clean publishToMavenLocal validatePlugins --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Plugin publication failed; inspect $log" >&2
  exit 1
fi
if ! grep -q 'BUILD SUCCESSFUL' "$log"; then
  echo 'Gradle did not report success' >&2
  exit 1
fi
if ! grep -q 'validatePlugins' "$log"; then
  echo 'validatePlugins did not run' >&2
  exit 1
fi

version=$(grep -E '^version=' gradle.properties | cut -d= -f2)
m2="$(m2_repository)/io/github/brody-0125"
for rel in \
  "spring-test-isolation-runtime/${version}/spring-test-isolation-runtime-${version}.jar" \
  "spring-test-isolation-gradle-plugin/${version}/spring-test-isolation-gradle-plugin-${version}.jar"; do
  if [[ ! -f "$m2/$rel" ]]; then
    echo "Missing Maven Local artifact: $rel" >&2
    exit 1
  fi
done
marker="$m2/spring-test-isolation/io.github.brody-0125.spring-test-isolation.gradle.plugin/${version}"
if [[ ! -e "$marker" ]]; then
  echo "Missing Gradle plugin marker under $marker" >&2
  exit 1
fi
pom="$m2/spring-test-isolation-runtime/${version}/spring-test-isolation-runtime-${version}.pom"
if ! grep -q 'MIT License' "$pom" || ! grep -q 'Seokhyeon Kim' "$pom"; then
  echo 'Runtime POM missing license or developer metadata' >&2
  exit 1
fi
echo "PASS: Maven Local publication, plugin marker, validatePlugins, POM metadata (version=$version)."
