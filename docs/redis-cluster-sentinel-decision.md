# Redis Cluster and Sentinel — spike decision

**Issue:** [#10](https://github.com/brody-0125/spring-test-isolation/issues/10)  
**Date:** 2026-09-19  
**Recommendation:** **Wontfix** for parallel Spring test worker isolation on shared Redis.

## Context

The library shares one Redis server per Gradle build and gives each worker its own logical database index, ACL user, key/channel namespace, and `FLUSHDB` reset path (`WorkerStore`, `RedisCacheWorkerBackend`). Spring apps are verified against **standalone** Lettuce; `ConnectionVerifier` rejects Cluster and Sentinel `LettuceConnectionFactory` configurations.

## Can the current model map to Cluster?

**No, not without replacing the isolation model.**

| Mechanism today | Standalone | Redis Cluster |
|-----------------|------------|---------------|
| Per-worker logical DB (`SELECT n`) | Yes (slots 1–255) | **No** — cluster mode uses DB 0 only |
| Per-worker `FLUSHDB` between classes | Full worker DB | Keys are sharded; flush semantics and Pub/Sub differ by topology |
| ACL namespace (`~*`, `&channel*`) | Used in fixtures | Key patterns still apply, but slot routing and client mode differ; not what we verify |
| Descriptor `redis.host` / `redis.port` | Single endpoint | Clients need cluster-aware discovery and slot handling |

Worker isolation assumes **cheap, deterministic reset of one logical database per worker**. Cluster is built for horizontal scale and slot migration, not for “255 mini-databases on one shared instance.”

## Can the current model map to Sentinel?

**Not as a supported target for this project.**

The Build Service writes a fixed `connection.properties` once at container start. Workers read static host/port for the build lifetime. Sentinel failover can change the master during a long build; we do not watch topology or rewrite the descriptor. Supporting Sentinel would mean lifecycle hooks for master changes and re-verifying every worker connection — outside the current Build Service contract.

## Product stance

Parallel **integration tests** need **correct isolation and fast reset**, not production HA topology. Running Testcontainers standalone Redis (or a Redis-compatible standalone such as Valkey) matches that goal. Requiring Cluster or Sentinel in test infrastructure adds operational cost without improving the isolation guarantees this library proves (C2, workflow fixtures, connection guard).

Production HA (Cluster/Sentinel) remains an application deployment concern. Consumers should point Spring at standalone Redis in tests even if production uses Cluster/Sentinel, unless they add their own integration suite.

## Decision

| Topology | Verdict | Rationale |
|----------|---------|-----------|
| Redis Cluster | **Wontfix** | Conflicts with per-worker logical DB + verified reset path; would be a new cache backend and contract, not an extension of `cache.backend=redis`. |
| Redis Sentinel | **Wontfix** | Failover vs fixed Build Service descriptor; no test-isolation benefit for this use case. |

Standalone Redis with Spring Data Redis Lettuce is the isolation model: dedicated storage and fixed connections per worker. Cluster/Sentinel do not fit that without a separate brief.

**Follow-up issues:** None. Implement would require a new isolation design (e.g. prefix-only on cluster without logical DB), new providers, and a full verification pass — intentionally out of scope for 1.x.

## Evidence inspected

- `runtime/.../RedisCacheWorkerBackend.java` — ACL provisioning, `FLUSHDB`, Cluster/Sentinel rejection in `verifyConnectionFactory`
- `runtime/.../WorkerStore.java` — `select(redisDatabase)`, fixed host/port from descriptor
- `README.md` limitation lists
