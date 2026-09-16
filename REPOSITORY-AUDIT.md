# Repository preparation audit

Date: 2026-09-17. Repository: `brody-0125/spring-test-isolation`.

## Corrections

| Finding | Correction |
|---|---|
| Prototype names differed from the chosen repository name | Renamed root and included Gradle builds, plugin ID, Maven group/artifact, Java packages, system properties, bean name, resource registrations, and README examples |
| The old Maven/Java namespace implied a GitHub account named after the prototype | Plugin ID `io.github.brody-0125.spring-test-isolation`, Maven group `io.github.brody-0125`, Java package `io.github.brody0125.springtestisolation` |
| README attributed context reuse to Smart Context | Spring TestContext caches and reuses contexts; Smart Context orders classes by configuration and closes contexts after their last use |
| Upstream library capabilities could be mistaken for this project's support | Documented this project's JUnit Jupiter and worker-JVM contract apart from Smart Context's broader framework and threaded-execution support |
| The consumer example omitted a test engine and Spring Boot integration dependencies | Added the tested Boot BOM, starter-test, JDBC and Redis starters, and JUnit launcher |
| Jedis and Lettuce had no clear distinction | Jedis handles runtime allocation/cleanup; the Spring connection verifier requires standalone Lettuce |
| Migration properties and excluded history table names could imply full migration compatibility | Documented that fixtures do not execute Flyway or Liquibase; their connection properties and history exclusions do not establish compatibility |
| Local evidence paths would be absent from a fresh clone | Marked build evidence as generated, ignored outputs and retained commands to reproduce it |
| Baseline measurements and expanded workflow results described different source states | Kept the original seven-test benchmark separate from the expanded eleven-test suite and the rename verification |
| Supporting documents still used a different language and prototype name | Updated CONTRACT.md and INTEGRATION-RESEARCH.md in English under the chosen name |

The Gradle extension remains `isolatedTests`. Diagnostic records retain the `PTK` marker and generated databases retain the `ptk_` prefix so the existing evidence parsers and historical logs remain comparable. These are internal labels, not plugin IDs or Maven coordinates. No artifact release existed before the rename, so this source uses the new public identifiers without aliases.

## Library references

- [spring-test-smart-context README at 3a587a0](https://github.com/seregamorph/spring-test-smart-context/blob/3a587a06942a8b55e12f662e68485e4dc4a02f93/README.md): dependency coordinates `com.github.seregamorph:spring-test-smart-context:1.0`, class grouping, eager context closure, and retention of required `@DirtiesContext` annotations. The source review does not extend this project's tested version matrix.
- Spring Modulith and Spring Session remain source references in [INTEGRATION-RESEARCH.md](INTEGRATION-RESEARCH.md), not runtime dependencies or tested compatibility claims.
- Spring Test, Spring Data Redis, Testcontainers, JUnit, and PostgreSQL dependencies retain the versions exercised by the fixtures. This audit corrects names and claims; it does not upgrade the version matrix to untested releases.

## Publication scope

The renamed build passed runtime tests, plugin validation, POM generation, a 2-worker full suite, a 2-worker workflow run, 22 tests across two modules, and seven expected failure scenarios. A separate consumer resolved the new plugin marker and runtime artifact from an isolated local Maven repository and passed a Spring Boot JDBC/Redis smoke test. See VERIFICATION.md for the distinction between these runs and the earlier benchmark.

The GitHub repository contains source, wrapper files, documentation, and verification scripts. Build outputs, local logs, credentials, and IDE metadata stay outside Git. Creating this repository does not publish Maven artifacts or a Gradle Plugin Portal release. README usage starts with Maven Local publication.
