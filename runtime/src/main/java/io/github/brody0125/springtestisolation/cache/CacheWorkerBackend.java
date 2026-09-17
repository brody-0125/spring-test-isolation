package io.github.brody0125.springtestisolation.cache;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Per-worker cache isolation on one shared cache server per build. */
public interface CacheWorkerBackend {
    String id();

    void provisionWorker(WorkerStore store, int logicalDatabase, String workerUser, String workerPassword) throws Exception;

    void resetWorkerData(WorkerStore store) throws Exception;

    void teardownWorker(WorkerStore store, int logicalDatabase, String workerUser) throws Exception;

    void grantChannelPattern(WorkerStore store, String pattern);

    void verifyConnectionFactory(RedisConnectionFactory source, WorkerStore store);
}
