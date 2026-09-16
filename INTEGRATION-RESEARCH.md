# Integration tests across connected resources

Review date: 2026-09-17. Target: Spring Test Isolation with the version combination in VERIFICATION.md. We inspected the public projects below at fixed commits and used their failure/recovery test patterns to design our own fixture. We did not run or port their full test suites.

## Source references

| Source | Pattern in the source | Use in this fixture |
|---|---|---|
| [Spring Modulith: PersistentDomainEventIntegrationTest](https://github.com/spring-projects/spring-modulith/blob/7638aec0179ccfde0037b52028627d0bbe0666c3/spring-modulith-events/spring-modulith-events-tests/src/test/java/example/events/PersistentDomainEventIntegrationTest.java) | Synchronous and asynchronous transactional listener failures, incomplete publications, resubmission, and invocation counts | Incomplete DB outbox entries, failure after Redis delivery before completion, and idempotent SQL projection after duplicate delivery |
| [Spring Session: RedisIndexedSessionRepositoryITests](https://github.com/spring-projects/spring-session/blob/d5d63f2b3b062a84b129498f695a28397aedc83b/spring-session-data-redis/src/integration-test/java/org/springframework/session/data/redis/RedisIndexedSessionRepositoryITests.java) | saves() checks data, a creation event, and an index, then deletion, index removal, and a destruction event; other tests cover principal changes, session ID changes, and expiry | Check asynchronous notifications and local received-event state alongside stored data, then clear them across class boundaries |

Spring Modulith and Spring Session are research references, not dependencies. We wrote the fixture without copying their source. These tests do not establish compatibility with either library. The separate spring-test-smart-context library is a runtime dependency.

## Fixture

HTTP → Redis lock → PostgreSQL transaction (orders + order_items + outbox) → Redis queue → asynchronous consumer → PostgreSQL projection → Redis cache → namespaced Pub/Sub → HTTP read.

WorkflowApplication.java uses the existing JDBC, Spring Data Redis, and web dependencies. Its Redis queue has one consumer that reads the head and removes it after processing. This is a test fixture with no production broker or outbox durability guarantee.

Four classes share a context configuration and reuse the same primary keys, SKU, Redis keys, and logical channel names. Worker-specific owner values identify cross-worker contamination. File barriers under the build's descriptor directory coordinate test steps; application data stays in PostgreSQL and Redis.

| Check | Assertion |
|---|---|
| Lock contention and Lua unlock | A foreign lock produces HTTP 409 without DB changes and retains its token; a successful request releases its own lock |
| DB transaction | An injected exception rolls back changes to all three tables without publishing to the queue or sending events |
| Publication retry | Failure after enqueue leaves an incomplete outbox entry; retry puts two copies of the event in the queue |
| Failure after projection commit | The consumer's SQL commit succeeds before an injected exception prevents the cache write |
| Reprocessing | One receipt, aggregate quantity 3, and one local processing count; retry restores the cache and drains the queue |
| Notification isolation | Two messages in the worker's namespace; duplicate notifications are allowed |
| Other worker cleanup | With live data in all workers, one calls WorkerStore.reset(); peers retain SQL aggregates and Redis cache entries |
| Class boundary | A latch holds pending work beyond the test body; quiesce releases it and waits for FK data, queue, key, and local state writes |
| Cleanup audit | A test listener with order 2970 checks five SQL tables and the Redis DB after runtime reset without reopening a closed context |
| Context reuse | The 1/2-worker runs reuse context identity across classes and restart the order identity at 1 |
| Resource disposal | Successful close logs, no cleanup warnings, and absence of the container IDs from Docker |

## Shutdown behavior found during testing

The initial fixture used StringRedisTemplate during bean close(). Spring had stopped LettuceConnectionFactory, so the write failed. Spring logged the exception as a warning and the Gradle test still passed.

The final fixture uses a worker-owned connection for deliberate destruction-time writes, allowing the test to check the runtime's later reset. Application work that needs Spring beans must finish in quiesce before context closure.

The verification script rejects bean close/destroy and worker storage cleanup warnings and requires WORKFLOW destroyed and WORKFLOW audited records. This is a fixture-level check; the runtime does not collect arbitrary Spring destruction exceptions.

## Limits

- Spring Session expiry tests use Redis system channels such as __keyevent@0__:expired. The current ACL grants worker namespace channels. Keyevent integration needs DB-specific channel permissions, server notification configuration, and subscription lifecycle tests.
- We inject exceptions at known application steps. We did not test server outages, network partitions, lost responses, or failover.
- PostgreSQL and Redis do not share a distributed transaction here. The fixture checks retry and DB deduplication, with no exactly-once guarantee for external effects.
- These tests exclude Hibernate second-level cache, actual Flyway/Liquibase migrations, Redis Streams consumer groups, Kafka/RabbitMQ, and multiple competing consumers.
- With four workers, each class runs in a separate JVM. The 1/2-worker runs establish context reuse. Workflow execution time is not a performance benchmark.

See VERIFICATION.md for commands and recorded results.
