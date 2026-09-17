# Publishing (Maven Central)

All public artifacts ship through **Maven Central** (Sonatype Central Portal). Consumers use `mavenCentral()` only — no JitPack or Gradle Plugin Portal for this project.

| Artifact | Role | Coordinates |
|----------|------|-------------|
| Runtime | Test dependency | `io.github.brody-0125:spring-test-isolation-runtime:VERSION` |
| Gradle plugin JAR | Plugin implementation | `io.github.brody-0125:spring-test-isolation-gradle-plugin:VERSION` |
| Plugin marker | Plugin ID resolution | `io.github.brody-0125.spring-test-isolation.gradle.plugin:io.github.brody-0125.spring-test-isolation.gradle.plugin.gradle.plugin:VERSION` |

Version is defined in [gradle.properties](gradle.properties).

## Consumer setup (after Central publish)

`settings.gradle`:

```groovy
pluginManagement {
    repositories { mavenCentral() }
}
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.brody-0125.spring-test-isolation' version '1.0.0'
}
repositories { mavenCentral() }
dependencies {
    testImplementation 'io.github.brody-0125:spring-test-isolation-runtime:1.0.0'
    // …
}
```

## Local development

```powershell
./gradlew :runtime:publishToMavenLocal
./gradlew -p plugin publishToMavenLocal
```

Use `mavenLocal()` in the consumer instead of `mavenCentral()` while testing.

## One-time publisher setup

1. Register at [central.sonatype.com](https://central.sonatype.com/) and verify namespace `io.github.brody-0125`.
2. Create an OpenPGP key and publish the public key to a keyserver.
3. Add repository **Secrets** (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |--------|--------|
   | `MAVEN_CENTRAL_USERNAME` | Sonatype user token username |
   | `MAVEN_CENTRAL_PASSWORD` | Sonatype user token password |
   | `GPG_PRIVATE_KEY` | Armored OpenPGP private key (full `BEGIN … END` block) |
   | `GPG_PASSPHRASE` | Private key passphrase |

## Publish a release

Pushing an annotated version tag triggers [.github/workflows/publish-maven-central.yml](.github/workflows/publish-maven-central.yml). The workflow strips the `v` prefix and passes `-Pversion=…` to Gradle (for example tag `v1.0.0` → version `1.0.0`).

```powershell
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

Signing uses in-memory key properties (`signingInMemoryKey` / `signingInMemoryKeyPassword`) from those secrets. For ad-hoc local publishes, set the same properties via `ORG_GRADLE_PROJECT_*` environment variables or `~/.gradle/gradle.properties` — never commit credentials.

Gradle uses the [Vanniktech Maven Publish](https://github.com/vanniktech/gradle-maven-publish-plugin) plugin. The `java-gradle-plugin` setup publishes both the plugin JAR and the **plugin marker** Maven publication to Central.

## Release order

See [RELEASING.md](RELEASING.md): merge → verify → tag → **publish to Maven Central** → GitHub Release.
