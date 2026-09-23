#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${VERSION:-$(grep '^version=' "$ROOT/gradle.properties" | cut -d= -f2)}"
cd "$ROOT"
./gradlew :runtime:publishToMavenLocal :maven-plugin:publishToMavenLocal -Pversion="$VERSION" --no-daemon
EVIDENCE="$ROOT/build/evidence/verify-maven"
mkdir -p "$EVIDENCE"
LOG="$EVIDENCE/mvn-test.log"
cd "$ROOT/verification-maven"
mvn -q -Dspring-test-isolation.version="$VERSION" test | tee "$LOG"
echo "Maven verification PASS (log: $LOG)"
