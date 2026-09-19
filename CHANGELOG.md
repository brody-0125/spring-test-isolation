# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **BREAKING:** The Gradle plugin no longer starts PostgreSQL or Redis. It attaches to consumer `spring.datasource.url` / `username` / `password` (required) and to Redis only when `spring.data.redis.host` or `spring.data.redis.url` is set. `redis.connection.*` does not attach Redis. Image override DSL (`postgresImage`, `mysqlImage`, `oracleImage`, `redisImage`, `redisLogicalDatabases`) is ignored. 1.1.x remains the line that starts containers.

## [1.1.0] - 2026-09-19

Minor release after **1.0.0**. Default `postgresql` + `redis` behavior is unchanged unless you opt into new backends or DSL overrides.

### Added

- Pluggable JDBC and cache backend SPI with `jdbcBackend` / `cacheBackend` on the `isolatedTests` extension; MySQL and Oracle JDBC backends alongside PostgreSQL; Redis cache backend.
- Consumer DSL overrides for infrastructure images and Redis capacity (`postgresImage`, `mysqlImage`, `oracleImage`, `redisImage`, `redisLogicalDatabases`, `maxCacheSlots`).
- Oracle JDBC worker backend with schema/user isolation and `scripts/verify-oracle`.
- `@Nested` tests that inherit the enclosing class Spring context. Storage reset still runs at the enclosing class. `@Nested` classes that declare their own context and `@ContextHierarchy` stay rejected.
- Native TestNG (`useTestNG()`) worker isolation: Smart Context suite ordering, sequential classes inside each worker, and a storage smoke fixture.
- Kotest on JUnit Platform (`kotest-runner-junit5`): specs covering worker storage, Smart Context eager close, and sequential execution inside each worker.
- Spring Session Redis keyevent ACL verification fixtures and `verify-spring-session` scripts.
- Bash verification scripts under `scripts/`; CI runs Docker verification on Linux. Job `config-cache` runs `scripts/verify-config-cache.sh -Workers 2`.

### Changed

- Plugin fails fast when `jdbcBackend` or `cacheBackend` is unknown.
- Gradle configuration fails when a TestNG task enables `parallel`, `threadCount` > 1, or suite XML.
- JUnit Platform tasks set `kotest.framework.parallelism=1`.

### Documentation

- Backend registration, descriptor properties, and override boundaries.
- Recorded configuration-cache PASS for `:runtime:test` and `:verification:test` with workers 1 and 2. The `plugin` included build is not claimed.
- Test framework compatibility matrix for JUnit, TestNG, and Kotest (closes spike in issue #11).
- Wontfix decision for Redis Cluster and Sentinel in test isolation ([docs/redis-cluster-sentinel-decision.md](docs/redis-cluster-sentinel-decision.md)).

## [1.0.0] - 2026-09-17

First stable release of Spring Test Isolation.

### Added

- Gradle plugin `io.github.brody-0125.spring-test-isolation` with shared Testcontainers Build Service (PostgreSQL and Redis).
- Runtime library for per-worker PostgreSQL databases, Redis logical databases, ACL accounts, and Pub/Sub namespace helpers.
- Spring `ContextCustomizerFactory`, connection verification for JDBC and Lettuce, and `ClassBoundary` hooks with timeout and worker poison semantics.
- Integration with [spring-test-smart-context 1.0](https://github.com/seregamorph/spring-test-smart-context) for class ordering and eager context closure.
- Verification module, PowerShell verification scripts (`verify.ps1`, `verify-failures.ps1`, `verify-workflow.ps1`, `verify-multimodule.ps1`, `verify-config-cache.ps1`), and multimodule `verification-peer` fixtures.
- Negative guards for concurrent JUnit execution, missing listeners, wrong connections, initialization failures, cleanup failures, `@Nested`, `@ContextHierarchy`, and `@Lazy` bypass DataSources.
- Flyway smoke fixture on worker databases; worker recovery and partial-allocation rollback probes.
- [CONTRIBUTING.md](CONTRIBUTING.md) contribution guide.

### Changed

- `WorkerStore.close()` and `Containers.close()` keep the first cleanup failure primary and attach later failures with `addSuppressed`.
- `BeforeBoundaryListener` rejects unsupported `@Nested` and `@ContextHierarchy` tests and re-verifies connections before each test method.
- Windows verification scripts tolerate Gradle JVM warnings on stderr; multimodule test counts updated for expanded fixtures.

### Documentation

- README restructured for 1.0.0 consumption, publication coordinates, and evidence boundaries.
- [RELEASING.md](RELEASING.md) documents the release and publication workflow.
- [PUBLISHING.md](PUBLISHING.md) and Maven Central Gradle configuration (Vanniktech Maven Publish; plugin marker + runtime).

[Unreleased]: https://github.com/brody-0125/spring-test-isolation/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/brody-0125/spring-test-isolation/releases/tag/v1.1.0
[1.0.0]: https://github.com/brody-0125/spring-test-isolation/releases/tag/v1.0.0
