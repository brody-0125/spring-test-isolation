package io.github.brody0125.springtestisolation;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import javax.sql.DataSource;

/** Validate connection beans before downstream SQL initializers can use them. */
final class ConnectionVerifier implements BeanPostProcessor {
    private final WorkerStore store;
    ConnectionVerifier(WorkerStore store) { this.store = store; }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        try {
            verify(bean, store);
            return bean;
        } catch (Exception e) {
            WorkerStore.poison(e);
            throw new IllegalStateException("Connection bean violates worker isolation: " + name, e);
        }
    }
    static void verify(Object bean, WorkerStore store) throws Exception {
        if (bean instanceof DataSource source) {
            try (var connection = source.getConnection()) {
                if (!store.database.equals(connection.getCatalog()) || !store.jdbcUrl.equals(connection.getMetaData().getURL()))
                    throw new IllegalStateException("DataSource bypasses worker database");
            }
        }
        if (bean instanceof RedisConnectionFactory source) {
            if (!(source instanceof LettuceConnectionFactory lettuce)
                    || lettuce.getDatabase() != store.redisDatabase
                    || !lettuce.getHostName().equals(store.redisHost()) || lettuce.getPort() != store.redisPort()
                    || lettuce.getClusterConfiguration() != null || lettuce.getSentinelConfiguration() != null
                    || !store.redisUsername().equals(lettuce.getStandaloneConfiguration().getUsername()))
                throw new IllegalStateException("RedisConnectionFactory bypasses worker storage or is unsupported");
        }
    }
}
