package io.github.brody0125.springtestisolation;

import org.springframework.test.context.*;
import org.springframework.core.env.MapPropertySource;
import java.util.*;

public final class IsolationCustomizerFactory implements ContextCustomizerFactory {
    @Override public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> config) {
        // Discovery must be pure: do not allocate a store or capture a random/class identity here.
        return new IsolationCustomizer();
    }
    static final class IsolationCustomizer implements ContextCustomizer {
        @Override public void customizeContext(org.springframework.context.ConfigurableApplicationContext context,
                                               MergedContextConfiguration merged) {
            WorkerStore store = WorkerStore.get();
            Map<String, Object> values = new HashMap<>();
            values.put("spring.datasource.url", store.jdbcUrl);
            values.put("spring.datasource.username", store.username());
            values.put("spring.datasource.password", store.password());
            values.put("spring.flyway.enabled", "false");
            values.put("spring.flyway.url", store.jdbcUrl);
            values.put("spring.flyway.user", store.username());
            values.put("spring.flyway.password", store.password());
            values.put("spring.liquibase.url", store.jdbcUrl);
            values.put("spring.liquibase.user", store.username());
            values.put("spring.liquibase.password", store.password());
            values.put("spring.datasource.hikari.maximum-pool-size", 3);
            values.put("spring.datasource.hikari.minimum-idle", 0);
            values.put("spring.data.redis.host", store.redisHost());
            values.put("spring.data.redis.port", store.redisPort());
            values.put("spring.data.redis.database", store.redisDatabase);
            values.put("spring.data.redis.username", store.redisUsername());
            values.put("spring.data.redis.password", store.redisPassword());
            values.put("springtestisolation.namespace", store.namespace);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("spring-test-isolation", values));
            context.getBeanFactory().registerSingleton("springTestIsolationWorkerStore", store);
            context.getBeanFactory().addBeanPostProcessor(new ConnectionVerifier(store));
            // registerSingleton does not register an inferred destroy method: Context never owns this store.
        }
        @Override public boolean equals(Object other) { return other instanceof IsolationCustomizer; }
        @Override public int hashCode() { return IsolationCustomizer.class.hashCode(); }
    }
}
