package io.github.brody0125.springtestisolation.cache;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import redis.clients.jedis.Jedis;

public final class RedisCacheWorkerBackend implements CacheWorkerBackend {
    public static final String ID = "redis";

    @Override public String id() { return ID; }

    @Override public void provisionWorker(WorkerStore store, int logicalDatabase, String workerUser, String workerPassword) throws Exception {
        try (Jedis admin = store.openAdminRedis()) {
            admin.aclSetUser(workerUser, "reset", "on", ">" + workerPassword, "~*", "&" + store.namespace + "*",
                    "&" + store.redisKeyeventChannel("del"), "&" + store.redisKeyeventChannel("expired"),
                    "+@all", "-@admin", "-@dangerous", "+flushdb", "+keys", "-swapdb", "-move", "-copy",
                    "-script|flush", "-function|flush", "-function|delete", "-function|load");
        }
    }

    @Override public void resetWorkerData(WorkerStore store) throws Exception {
        try (Jedis redis = store.openWorkerRedis()) { redis.flushDB(); }
    }

    @Override public void teardownWorker(WorkerStore store, int logicalDatabase, String workerUser) throws Exception {
        try (Jedis admin = store.openAdminRedis()) {
            admin.select(logicalDatabase);
            admin.flushDB();
            admin.aclDelUser(workerUser);
        }
    }

    @Override public void grantChannelPattern(WorkerStore store, String pattern) {
        try (Jedis admin = store.openAdminRedis()) { admin.aclSetUser(store.database, "&" + pattern); }
    }

    @Override public void verifyConnectionFactory(RedisConnectionFactory source, WorkerStore store) {
        if (!(source instanceof LettuceConnectionFactory lettuce)
                || lettuce.getDatabase() != store.redisDatabase
                || !lettuce.getHostName().equals(store.redisHost()) || lettuce.getPort() != store.redisPort()
                || lettuce.getClusterConfiguration() != null || lettuce.getSentinelConfiguration() != null
                || !store.redisUsername().equals(lettuce.getStandaloneConfiguration().getUsername()))
            throw new IllegalStateException("RedisConnectionFactory bypasses worker storage or is unsupported");
    }
}
