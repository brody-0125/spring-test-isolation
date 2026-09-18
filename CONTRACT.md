# Execution contract

## Objective

Run Spring test classes across Gradle worker JVMs while sharing one PostgreSQL container and one Redis container through a Gradle Build Service. Keep classes and methods in sequence within each worker. Spring TestContext owns context caching and reuse. The spring-test-smart-context dependency groups classes by configuration and closes contexts after their last use.

## Immutable compatibility criteria

| ID | Contract | Implementation and verification |
|---|---|---|
| C1 | Parallelism across workers; classes and methods in sequence within each worker | Gradle ValidateSettings, JUnit ExecutionGuard, TestNGExecutionGuard, BoundaryState, and interval overlap checks |
| C2 | Dedicated storage and fixed connections per worker | WorkerStore; identical keys and channel names; fresh storage after a crash |
| C3 | Reuse contexts with the same configuration; no class IDs or random cache keys | IsolationCustomizer equality and context identity checks |
| C4 | Deterministic configuration discovery without side effects | ContextCustomizerFactory and DiscoveryTest without a descriptor |
| C5 | Smart Context owns ordering and eager closure | Required orderer and listener; closure after a configuration group's last class |
| C6 | Separate context, storage, and container lifetimes | registerSingleton without destruction ownership; configuration groups share worker storage |
| C7 | Previous classes must not contaminate later classes | ClassBoundary hooks, delayed writes, and local state reset fixtures |
| C8 | Quiesce before context closure; reset storage after closure; poison the worker on failure | Listener orders 3001/2980, poison state, failure and timeout checks |
| C9 | Preserve required extensions and reject detected conflicts | Gradle validation, independent JUnit and TestNG guards, connection BeanPostProcessor, and negative fixtures |
| C10 | Execute tests before claiming a version combination works | VERIFICATION.md, initialization failures, child JVM halt/retry, and multiple modules |

## Ownership and failure handling

The Build Service owns containers. A worker owns its PostgreSQL database, Redis logical database, and Redis ACL account. Spring contexts own connection pools and clients. The application's ClassBoundary adapter stops background work and resets local state; the runtime controls hook order and time limits.

Workers allocate storage through atomic file creation. A crashed worker's storage remains reserved for the rest of the build, and container shutdown removes it. Each build starts fresh containers. A failed worker allocation poisons the worker and leaves any claimed slot file in place until the build-owned descriptor directory is removed; a shutdown hook registers only after allocation succeeds. Initialization or cleanup failure, or a detected execution policy conflict, blocks subsequent test bodies in that worker.

Keep required @DirtiesContext annotations for context changes that the hooks cannot undo. Neither context caching nor Smart Context's eager closure replaces the application's cleanup contract.

## Scope

The tested paths use PostgreSQL JDBC and standalone Redis with Spring Data Redis Lettuce by default (`jdbc.backend` / `cache.backend`); additional engines register through the JDBC and cache worker backends described in VERIFICATION.md. JUnit Jupiter, TestNG (`useTestNG()`), and spring-test-smart-context 1.0 apply to all backends. Kotest is not verified. Pub/Sub requires the provided namespace helpers. @Nested test classes and @ContextHierarchy are rejected. Flyway against the worker database is covered by a smoke fixture only; Liquibase is not executed in fixtures. Native clients, static state, untracked tasks, and server-wide changes need explicit integration. Schema changes and external state require reset adapters beyond the built-in table cleanup.

The library assumes trusted test code. Redis ACLs do not prevent arbitrary SELECT commands from choosing another logical database.

## Evidence

Record passing fixtures, expected failures, child JVM crash/retry, shared containers across modules, and resource measurements. Source review alone does not establish a runtime PASS. Keep claims within the tested version combination and access contract.
