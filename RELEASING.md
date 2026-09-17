# Releasing

This document describes how to cut a **1.0.0** (or later) release from `main`.

## Preconditions

- [VERIFICATION.md](VERIFICATION.md) reflects the version matrix you are shipping.
- On `main`, run the verification scripts listed in [README.md](README.md) and record logs under `build/evidence/` when publishing release notes.
- [CHANGELOG.md](CHANGELOG.md) contains a section for the target version with the release date filled in.

## Version alignment

The Gradle `version` is set in the root [build.gradle](build.gradle) (`allprojects { version = '…' }`). Subprojects inherit it unless they override. Before tagging, confirm:

```powershell
./gradlew properties -q | Select-String "^version:"
```

Consumer coordinates for **1.0.0**:

| Artifact | Coordinates |
|----------|-------------|
| Gradle plugin | `io.github.brody-0125.spring-test-isolation:1.0.0` |
| Runtime JAR | `io.github.brody-0125:spring-test-isolation-runtime:1.0.0` |

Artifacts publish to **Maven Central** only; see [PUBLISHING.md](PUBLISHING.md) for accounts, credentials, and commands.

## Local publication smoke test

```powershell
./gradlew :runtime:publishToMavenLocal
./gradlew -p plugin publishToMavenLocal
./gradlew -p plugin validatePlugins
```

Use the consumer example in [README.md](README.md) with `mavenLocal()` and the `version` from [gradle.properties](gradle.properties).

## Git tag and GitHub release

1. Merge the release preparation PR into `main`.
2. Tag the merge commit:

   ```powershell
   git tag -a v1.0.0 -m "Release 1.0.0"
   git push origin v1.0.0
   ```

3. Pushing the tag runs the **Publish to Maven Central** workflow; confirm it succeeds under Actions (see [PUBLISHING.md](PUBLISHING.md) for required GitHub Secrets).
4. Create a GitHub release from tag `v1.0.0` and paste the `[1.0.0]` section from [CHANGELOG.md](CHANGELOG.md).

## After release

- Open a follow-up PR bumping to `1.0.1-SNAPSHOT` (or the next development version) on `main` if you continue snapshot development.
