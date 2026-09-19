# Spring Test Isolation

Gradle plugin and runtime for parallel Spring tests across worker JVMs with **per-worker** storage isolation.

The plugin attaches to PostgreSQL the consumer already runs. Redis attaches only when `spring.data.redis.host` or `spring.data.redis.url` is set. It does not start containers. A Gradle Build Service owns the descriptor used for allocation, poison, and cleanup.

| | |
|---|---|
| **License** | [MIT](LICENSE.md) — Copyright © 2026 Seokhyeon Kim |
| **JDK** | 17 |
| **Contributing** | [CONTRIBUTING.md](CONTRIBUTING.md) |

## What it does

- Runs test **classes** in parallel across Gradle workers; keeps classes and methods **sequential** inside each worker.
- Shares one consumer-started PostgreSQL server per build (and Redis only when `spring.data.redis.host` or `spring.data.redis.url` is set); each worker gets its own database, and a Redis DB index, ACL user, and Pub/Sub namespace when Redis is attached. The Gradle Build Service owns the descriptor used for allocation, poison, and cleanup — not container processes.
- Reuses Spring contexts via TestContext; [spring-test-smart-context](https://github.com/seregamorph/spring-test-smart-context) orders classes and closes contexts after the last class in a configuration group.
- Clears worker storage between classes (`TRUNCATE` / `FLUSHDB`), runs application `ClassBoundary` hooks, and **poisons** the worker on cleanup or policy failures.

Unsupported: `@Nested` with its own context, `@ContextHierarchy`, Redis Cluster/Sentinel (wontfix for test isolation — [decision note](docs/redis-cluster-sentinel-decision.md)). `@Nested` that inherits the enclosing class context is supported; nested classes share worker storage until the enclosing class ends. Supported runners: JUnit Jupiter (default), TestNG (`useTestNG()`), Kotest on JUnit Platform (`kotest-runner-junit5`). The standalone Kotest Gradle plugin is unsupported.

## Repository layout

| Module | Role |
|--------|------|
| `plugin` | `io.github.brody-0125.spring-test-isolation` Gradle plugin (descriptor Build Service; does not start containers) |
| `runtime` | Spring customizer, `WorkerStore`, listeners, guards |
| `verification` / `verification-peer` | Docker integration fixtures (not published) |

## Quick start (consumer project)

**Requirements:** JDK 17, Gradle with JUnit Platform (default, including Kotest) or TestNG (`test { useTestNG() }`), and a PostgreSQL server the tests can reach (`spring.datasource.url` / `username` / `password`). Redis is optional until the application enables a Spring Data Redis client. Docker is required only if **you** start those servers with Testcontainers or Compose — the plugin does not start them.

Publication uses [gradle.properties](gradle.properties) for the version. **Maven Central** is the only public registry — see [PUBLISHING.md](PUBLISHING.md) (plugin marker + runtime JAR).

**Maven Local** (development, from a clone of this repo):

```powershell
./gradlew :runtime:publishToMavenLocal
./gradlew -p plugin publishToMavenLocal
```

**Coordinates** (version in [gradle.properties](gradle.properties)):

| | Value |
|---|---|
| Plugin ID | `io.github.brody-0125.spring-test-isolation` |
| Runtime | `io.github.brody-0125:spring-test-isolation-runtime:1.1.0` |
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
    id 'io.github.brody-0125.spring-test-isolation' version '1.1.0'
}
repositories { mavenLocal(); mavenCentral() }
dependencies {
    testImplementation 'io.github.brody-0125:spring-test-isolation-runtime:1.1.0'
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.5.1')
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-jdbc'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-redis'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
isolatedTests {
    workers = 2
    jdbcBackend = 'postgresql' // default
    cacheBackend = 'redis'     // default; Redis attaches only when the app enables it
    // maxCacheSlots = 255
    // postgresImage / mysqlImage / oracleImage / redisImage / redisLogicalDatabases are ignored
}
```

Point tests at your server (application properties, `@SpringBootTest`, or JVM args). Empty `spring.datasource.url` fails before test bodies run. `redis.connection.*` alone does not attach Redis.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=secret
# Only if the application uses Spring Data Redis:
# spring.data.redis.host=localhost
# spring.data.redis.port=6379
```

This repository’s `verification/build.gradle` is a full example: it starts PostgreSQL and Redis with Testcontainers and injects those properties.

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

**Evidence scripts (Docker required):** same checks in `scripts/` as PowerShell (`.ps1`) or Bash (`.sh`). On Linux or macOS, run `chmod +x scripts/*.sh` once if needed.

```bash
./scripts/verify.sh -Workers 2
./scripts/verify-failures.sh
./scripts/verify-multimodule.sh
./scripts/verify-workflow.sh -Workers 2 -Runs 3
./scripts/verify-spring-session.sh -Workers 2
./scripts/verify-config-cache.sh -Workers 2
./scripts/verify-oracle.sh -Workers 2
```

```powershell
./scripts/verify.ps1 -Workers 2
./scripts/verify-failures.ps1
./scripts/verify-multimodule.ps1
./scripts/verify-workflow.ps1 -Workers 2 -Runs 3
./scripts/verify-spring-session.ps1 -Workers 2
./scripts/verify-config-cache.ps1 -Workers 2
./scripts/verify-oracle.ps1 -Workers 2
```

`verify-failures` expects deliberate Gradle failures; the default `test` task excludes `negative` and `guard` tags.

## Documentation

| Document | Contents |
|----------|----------|
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [PUBLISHING.md](PUBLISHING.md) | Maven Central publication |
| [RELEASING.md](RELEASING.md) | Tagging and GitHub Release steps |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branches, commits, PRs |

## Compatibility summary

1. Parallel workers; sequential execution within each worker.  
2. Fixed worker database and Redis DB for the JVM lifetime.  
3. Pure configuration customizers (no per-class random keys).  
4. Smart Context ordering and listeners required.  
5. Worker storage survives context closure; consumer infrastructure survives workers.  
6. Cleanup failure or policy conflict poisons the worker.  
7. Claims require executed tests — not documentation alone.

## License

Released under the [MIT License](LICENSE.md).
