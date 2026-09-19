# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- CI job `config-cache` runs `scripts/verify-config-cache.sh -Workers 2`.
- `@Nested` tests that inherit the enclosing class Spring context. Storage reset still runs at the enclosing class. `@Nested` classes that declare their own context and `@ContextHierarchy` stay rejected.
- Native TestNG (`useTestNG()`) worker isolation: Smart Context suite ordering, sequential classes inside each worker, and a storage smoke fixture.
- Kotest on JUnit Platform (`kotest-runner-junit5`): four specs covering worker storage, Smart Context eager close, and sequential execution inside each worker.
- Gradle configuration fails when a TestNG task enables `parallel`, `threadCount` > 1, or suite XML.

### Changed

- JUnit Platform tasks set `kotest.framework.parallelism=1`.

### Documentation

- Recorded configuration-cache PASS for `:runtime:test` and `:verification:test` with workers 1 and 2. The `plugin` included build is not claimed.

## [1.0.0] - 2026-09-17

First stable release of Spring Test Isolation for the verified version matrix in [VERIFICATION.md](VERIFICATION.md).

### Added

- Gradle plugin `io.github.brody-0125.spring-test-isolation` with shared Testcontainers Build Service (PostgreSQL and Redis).
- Runtime library for per-worker PostgreSQL databases, Redis logical databases, ACL accounts, and Pub/Sub namespace helpers.
- Spring `ContextCustomizerFactory`, connection verification for JDBC and Lettuce, and `ClassBoundary` hooks with timeout and worker poison semantics.
- Integration with [spring-test-smart-context 1.0](https://github.com/seregamorph/spring-test-smart-context) for class ordering and eager context closure.
- Verification module, PowerShell verification scripts (`verify.ps1`, `verify-failures.ps1`, `verify-workflow.ps1`, `verify-multimodule.ps1`, `verify-config-cache.ps1`), and multimodule `verification-peer` fixtures.
- Negative guards for concurrent JUnit execution, missing listeners, wrong connections, initialization failures, cleanup failures, `@Nested`, `@ContextHierarchy`, and `@Lazy` bypass DataSources.
- Flyway smoke fixture on worker databases; worker recovery and partial-allocation rollback probes.
- [CONTRIBUTING.md](CONTRIBUTING.md) and [CONTRACT.md](CONTRACT.md) execution contract.

### Changed

- `WorkerStore.close()` and `Containers.close()` keep the first cleanup failure primary and attach later failures with `addSuppressed`.
- `BeforeBoundaryListener` rejects unsupported `@Nested` and `@ContextHierarchy` tests and re-verifies connections before each test method.
- Windows verification scripts tolerate Gradle JVM warnings on stderr; multimodule test counts updated for expanded fixtures.

### Documentation

- README restructured for 1.0.0 consumption, publication coordinates, and evidence boundaries.
- [RELEASING.md](RELEASING.md) documents the release and publication workflow.
- [PUBLISHING.md](PUBLISHING.md) and Maven Central Gradle configuration (Vanniktech Maven Publish; plugin marker + runtime).

[Unreleased]: https://github.com/brody-0125/spring-test-isolation/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/brody-0125/spring-test-isolation/releases/tag/v1.0.0
