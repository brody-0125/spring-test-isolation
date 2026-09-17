# Spring Test Isolation

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch names, commit messages, and PR expectations.

Run Spring test classes in parallel across Gradle workers. The build shares one PostgreSQL container and one Redis container. Each worker uses its own PostgreSQL database, Redis logical database, and Pub/Sub namespace. Classes and methods run in sequence within a worker. Spring TestContext caches and reuses contexts; `spring-test-smart-context` groups classes by configuration and closes a context after its last class.

This prototype targets JUnit Jupiter, Spring Boot 3.5.1, and the versions in [VERIFICATION.md](VERIFICATION.md). The pinned versions describe the tested combination, not the latest releases.

## Components

- `plugin`: The `io.github.brody-0125.spring-test-isolation` Gradle plugin and shared Build Service.
- `runtime`: Spring connection configuration, worker storage, class boundary hooks, and execution policy checks.
- `verification`: A verification project using real PostgreSQL, Redis, HTTP, and background tasks.
- `verification-peer`: Runs the same fixtures in a separate module to verify Build Service sharing.

Requirements: JDK 17 and Docker with Linux containers. Use the Gradle Wrapper.

```powershell
./gradlew :runtime:test :verification:test
```

On Windows, run these scripts to execute the checks and evaluate their results:

```powershell
./verify.ps1 -Workers 2
./verify-failures.ps1
./verify-multimodule.ps1
./verify-workflow.ps1 -Workers 1
./verify-workflow.ps1 -Workers 2 -Runs 3
./verify-workflow.ps1 -Workers 4
./measure.ps1 -Workers 1
./measure.ps1 -Workers 2
```

`verify-failures.ps1` checks expected Gradle failures and confirms that forbidden test bodies did not execute. The regular `test` task excludes negative fixtures. `measure.ps1` samples Windows test JVM Working Set and Private Bytes, excluding Docker and the Gradle daemon. Run measurements without other builds in progress.

`verify-workflow.ps1` runs four classes through HTTP, a database transaction and outbox, a Redis queue, asynchronous aggregation, a cache, and Pub/Sub. With 2 or 4 workers, barriers hold the workers at the same step so the tests can check that clearing one worker's storage preserves another worker's data.

The regular `test` task includes these classes. Use `workflowTest` for the barrier checks, running all four classes with 1, 2, or 4 workers. A subset selected with `--tests` may leave workers waiting at a barrier. [INTEGRATION-RESEARCH.md](INTEGRATION-RESEARCH.md) lists the public projects consulted and the verification scope.

The cleanup failure check expects a failed build: the first class's test body runs, cleanup fails, and the second class's body must not run.

```powershell
./gradlew :verification:cleanupFailureTest -Pworkers=1
```

## Compatibility contract

1. Run tests in parallel across Gradle workers; keep classes and methods in sequence within each worker.
2. The database name and Redis database number remain fixed for the worker's lifetime.
3. Customizers must not contain class or method IDs, or a different UUID on each invocation.
4. Configuration discovery does not access storage and produces the same result on repeated runs.
5. Smart Context owns test ordering and automatic context closure.
6. Closing a context does not delete worker storage or shared containers.
7. Between classes, stop accepting background work, wait for existing work to finish, and reset state.
8. A cleanup failure poisons the worker and blocks subsequent Spring test bodies.
9. Preserve the Smart Context orderer and listeners, and fail on detected conflicts.
10. Mark a version combination as verified after executing its tests.

## Application integration

Apply both the plugin and the runtime library. Use `verification/build.gradle` as a runnable example. Maven Central and the Gradle Plugin Portal do not host a release of this project; publish to Maven Local before using the coordinates below.

| Name | Value |
|---|---|
| Repository | `brody-0125/spring-test-isolation` |
| Gradle plugin ID | `io.github.brody-0125.spring-test-isolation` |
| Runtime coordinates | `io.github.brody-0125:spring-test-isolation-runtime:0.1.0-SNAPSHOT` |
| Java package | `io.github.brody0125.springtestisolation` |
| System property prefix | `springtestisolation` |
| Gradle extension | `isolatedTests` |

To use the artifacts in another project, publish them to your local Maven repository:

```powershell
./gradlew :runtime:publishToMavenLocal
./gradlew -p plugin publishToMavenLocal
```

In the consuming project's `settings.gradle`:

```groovy
pluginManagement { repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() } }
```

In the consuming project's `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.brody-0125.spring-test-isolation' version '0.1.0-SNAPSHOT'
}
repositories { mavenLocal(); mavenCentral() }
dependencies {
    testImplementation 'io.github.brody-0125:spring-test-isolation-runtime:0.1.0-SNAPSHOT'
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.5.1')
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-jdbc'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-redis'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
isolatedTests { workers = 2 }
```

Tests sharing worker storage must use compatible database server settings and migration configurations. Put incompatible schema or migration configurations in separate Test tasks. Keep the worker database alive across context closures.

Set the worker count through `isolatedTests.workers`. The example accepts `-Pworkers=2`. The Build Service owns the containers outside Spring. Each context receives the worker's existing WorkerStore and leaves it alive on shutdown.

The runtime checks JDBC and Spring Data Redis Lettuce connections. You must check clients you create outside these paths. Use `WorkerStore.channel(logicalName)` for Pub/Sub publishing and ordinary subscriptions, and `WorkerStore.channelPattern(logicalPattern)` for pattern subscriptions. The pattern helper registers the exact ACL pattern Redis PSUBSCRIBE requires. Pub/Sub needs this namespace because Redis logical databases do not isolate channels.

Jedis serves the runtime's worker allocation and cleanup operations; it is not a supported Spring RedisConnectionFactory replacement. The connection verifier requires standalone Lettuce and rejects Sentinel and Cluster configurations.

The runtime includes [spring-test-smart-context 1.0](https://github.com/seregamorph/spring-test-smart-context) as a dependency. Its upstream support for other frameworks and threaded execution does not expand this project's contract: use JUnit Jupiter with parallelism across Gradle workers. Keep `@DirtiesContext` where a test changes context state that the boundary hooks cannot restore. Smart Context's job is class ordering and eager closure, not data isolation or cross-JVM context sharing.

Applications with background tasks or local state must provide a `ClassBoundary` bean:

- `quiesce`: Reject new work and wait for existing work to finish within a bounded time.
- `reset`: After the runtime clears storage, reset local caches and state and restore required seed data. Keep work paused.
- `resume`: Allow work when the next class starts.

Hooks run on a dedicated daemon thread with a default timeout of 10 seconds per phase. Set the timeout through `springtestisolation.boundaryTimeoutMillis`. On timeout, the runtime requests interruption, poisons the worker, and blocks subsequent tests. Your hook must handle interruption; the runtime cannot force an uncooperative task to stop without risking application state.

Your adapter must track application threads, schedulers, and external processes and confirm that their work has stopped before the next class. The Work bean in `verification` shows how to wait for delayed work with a barrier.

## Data reset contract

After each class, the runtime clears user tables in the worker database with TRUNCATE RESTART IDENTITY CASCADE and clears that worker's Redis database with FLUSHDB. It excludes the default Flyway and Liquibase history table names from the truncate list. Your tests handle cleanup between methods within a class. Restore required migration seed data in the reset hook. Schema changes, custom migration history tables, independent sequences, and large objects require a separate reset adapter. Flyway/Liquibase connection properties are configured, but the fixtures do not execute either migration library; this is not a migration compatibility claim.

Each worker uses a separate Redis ACL account. The ACL blocks global or administrative commands such as FLUSHALL and channels outside the worker's namespace. Connections require SELECT, which can also select another worker's logical database. Run trusted tests through the supported connections: arbitrary SELECT, access to other storage areas, server configuration changes, and server restarts fall outside the isolation contract. The library provides no security sandbox for malicious test code.

## Lifecycles and resources

Workers claim Redis storage areas by creating files with an atomic operation in the build's descriptor directory. After a worker crashes, its area remains reserved until the build ends. A build permits 255 worker allocations by default and fails on exhaustion. Each build starts fresh containers.

The default Hikari maximum pool size is 3 per context. Size your worker count and distinct context configurations against available memory and connection capacity. Each JVM and context adds overhead; the library cannot guarantee OOM prevention or faster execution.

## Evidence boundaries

Use `VERIFICATION.md` to check executed tests and their limits. Source and documentation review does not earn a PASS. Before using other Spring Boot, JUnit, or Gradle versions, Redis Cluster, TestNG/Kotest, context hierarchies, or configuration cache, run compatibility checks for that configuration.
