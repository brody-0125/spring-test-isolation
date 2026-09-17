package io.github.brody0125.springtestisolation;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.connection.RedisConnectionFactory;

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
        if (bean instanceof DataSource source) store.jdbcBackend.verifyDataSource(source, store);
        if (bean instanceof RedisConnectionFactory source) store.cacheBackend.verifyConnectionFactory(source, store);
    }
}
