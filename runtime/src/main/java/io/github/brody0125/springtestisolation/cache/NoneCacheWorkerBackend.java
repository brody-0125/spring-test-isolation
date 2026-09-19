package io.github.brody0125.springtestisolation.cache;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** No cache server. JDBC-only suites must not require Redis. */
public final class NoneCacheWorkerBackend implements CacheWorkerBackend {
    public static final String ID = "none";

    @Override public String id() { return ID; }

    @Override public void provisionWorker(WorkerStore store, int logicalDatabase, String workerUser, String workerPassword) {}

    @Override public void resetWorkerData(WorkerStore store) {}

    @Override public void teardownWorker(WorkerStore store, int logicalDatabase, String workerUser) {}

    @Override public void grantChannelPattern(WorkerStore store, String pattern) {}

    @Override public void verifyConnectionFactory(RedisConnectionFactory source, WorkerStore store) {}
}
