# Spring Test Isolation

**Version 1.0.0** — Gradle plugin and runtime library for parallel Spring tests across worker JVMs with shared PostgreSQL and Redis containers and **per-worker** storage isolation.

| | |
|---|---|
| **License** | [MIT](LICENSE.md) — Copyright © 2026 Seokhyeon Kim |
| **JDK** | 17 |
| **Verified stack** | Spring Boot 3.5.1, JUnit Jupiter 5.12, Smart Context 1.0 — see [VERIFICATION.md](VERIFICATION.md) |
| **Contributing** | [CONTRIBUTING.md](CONTRIBUTING.md) |

## What it does

- Runs test **classes** in parallel across Gradle workers; keeps classes and methods **sequential** inside each worker.
- Shares one PostgreSQL and one Redis container per build (Gradle Build Service); each worker gets its own database, Redis DB index, ACL user, and Pub/Sub namespace.
- Reuses Spring contexts via TestContext; [spring-test-smart-context](https://github.com/seregamorph/spring-test-smart-context) orders classes and closes contexts after the last class in a configuration group.
- Clears worker storage between classes (`TRUNCATE` / `FLUSHDB`), runs application `ClassBoundary` hooks, and **poisons** the worker on cleanup or policy failures.

Unsupported in 1.0.0: `@Nested`, `@ContextHierarchy`, Redis Cluster/Sentinel, TestNG/Kotest — see [VERIFICATION.md](VERIFICATION.md).

## Repository layout

| Module | Role |
|--------|------|
| `plugin` | `io.github.brody-0125.spring-test-isolation` Gradle plugin and Testcontainers Build Service |
| `runtime` | Spring customizer, `WorkerStore`, listeners, guards |
| `verification` / `verification-peer` | Docker integration fixtures (not published) |

## Quick start (consumer project)

**Requirements:** JDK 17, Docker (Linux engine), Gradle with JUnit Platform.

Publication uses [gradle.properties](gradle.properties) for the version. **Maven Central** is the only public registry — see [PUBLISHING.md](PUBLISHING.md) (plugin marker + runtime JAR).

**Maven Local** (development, from a clone of this repo):

```powershell
./gradlew :runtime:publishToMavenLocal
./gradlew -p plugin publishToMavenLocal
```

**Coordinates (1.0.0)**

| | Value |
|---|---|
| Plugin ID | `io.github.brody-0125.spring-test-isolation` |
| Runtime | `io.github.brody-0125:spring-test-isolation-runtime:1.0.0` |
| Java package | `io.github.brody0125.springtestisolation` |
| Gradle extension | `isolatedTests` |

`settings.gradle` (use `mavenLocal()` while testing unpublished builds):

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
repositories { mavenLocal(); mavenCentral() }
dependencies {
    testImplementation 'io.github.brody-0125:spring-test-isolation-runtime:1.0.0'
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.5.1')
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-jdbc'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-redis'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
isolatedTests {
    workers = 2
    jdbcBackend = 'postgresql' // default
    cacheBackend = 'redis'     // default
    // Optional (defaults match VERIFICATION.md):
    // postgresImage = 'postgres:16.9-alpine'
    // redisImage = 'redis:7.4.4-alpine'
    // redisLogicalDatabases = 256
    // maxCacheSlots = 255
}
```

Container image overrides are optional; changing them moves you outside the verified stack until you re-run the verification scripts and update [VERIFICATION.md](VERIFICATION.md).

Use `verification/build.gradle` in this repository as a full working example.

## Application responsibilities

Implement a `ClassBoundary` bean when you have background work or local state:

| Phase | Purpose |
|-------|---------|
| `quiesce` | Stop accepting work; wait for in-flight work (bounded) |
| `reset` | After runtime storage reset, restore caches/seed data |
| `resume` | Allow work when the next class starts |

Default hook timeout: 10s (`springtestisolation.boundaryTimeoutMillis`). Timeouts poison the worker.

Use `WorkerStore.channel(name)` and `WorkerStore.channelPattern(pattern)` for Pub/Sub. The runtime verifies **JDBC** and **Lettuce** standalone beans; check other clients yourself.

Keep `@DirtiesContext` when hooks cannot undo context mutations.

## Develop in this repository

```powershell
./gradlew :runtime:test :verification:test
```

**Evidence scripts (Windows, Docker required):**

```powershell
./verify.ps1 -Workers 2
./verify-failures.ps1
./verify-multimodule.ps1
./verify-workflow.ps1 -Workers 2 -Runs 3
./verify-config-cache.ps1 -Workers 2
```

`verify-failures.ps1` expects deliberate Gradle failures; the default `test` task excludes `negative` and `guard` tags.

## Documentation

| Document | Contents |
|----------|----------|
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [CONTRACT.md](CONTRACT.md) | Isolation contract (C1–C10) |
| [VERIFICATION.md](VERIFICATION.md) | Tested versions and evidence |
| [INTEGRATION-RESEARCH.md](INTEGRATION-RESEARCH.md) | External references and scope |
| [PUBLISHING.md](PUBLISHING.md) | Maven Central publication |
| [RELEASING.md](RELEASING.md) | Tagging and GitHub Release steps |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branches, commits, PRs |

## Compatibility summary

1. Parallel workers; sequential execution within each worker.  
2. Fixed worker database and Redis DB for the JVM lifetime.  
3. Pure configuration customizers (no per-class random keys).  
4. Smart Context ordering and listeners required.  
5. Worker storage survives context closure; containers survive workers.  
6. Cleanup failure or policy conflict poisons the worker.  
7. Claims require executed tests — not documentation alone.

## License

Released under the [MIT License](LICENSE.md).
