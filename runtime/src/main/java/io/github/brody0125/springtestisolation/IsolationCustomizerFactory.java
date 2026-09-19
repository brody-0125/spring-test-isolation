package io.github.brody0125.springtestisolation;

import org.springframework.test.context.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import java.net.URI;
import java.util.*;

public final class IsolationCustomizerFactory implements ContextCustomizerFactory {
    @Override public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> config) {
        // Discovery must be pure: do not allocate a store or capture a random/class identity here.
        return new IsolationCustomizer();
    }

    static Properties attachOverlay(Environment env) {
        String url = env.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "spring.datasource.url is required; spring-test-isolation attaches to consumer JDBC and does not start a database");
        }
        Properties overlay = new Properties();
        overlay.setProperty(InfrastructureDescriptor.JDBC_URL, url);
        String user = env.getProperty("spring.datasource.username");
        if (user != null) overlay.setProperty(InfrastructureDescriptor.JDBC_USER, user);
        String password = env.getProperty("spring.datasource.password");
        if (password != null) overlay.setProperty(InfrastructureDescriptor.JDBC_PASSWORD, password);
        if (redisClientEnabled(env)) {
            overlay.setProperty(InfrastructureDescriptor.CACHE_BACKEND, InfrastructureDescriptor.DEFAULT_CACHE_BACKEND);
            applyRedis(env, overlay);
        } else {
            overlay.setProperty(InfrastructureDescriptor.CACHE_BACKEND, InfrastructureDescriptor.NONE_CACHE_BACKEND);
        }
        return overlay;
    }

    static boolean redisClientEnabled(Environment env) {
        return notBlank(env.getProperty("spring.data.redis.host")) || notBlank(env.getProperty("spring.data.redis.url"));
    }

    private static void applyRedis(Environment env, Properties overlay) {
        String redisUrl = env.getProperty("spring.data.redis.url");
        String host;
        String port;
        if (notBlank(redisUrl)) {
            URI uri = URI.create(redisUrl);
            host = uri.getHost();
            int parsed = uri.getPort();
            port = parsed > 0 ? Integer.toString(parsed) : env.getProperty("spring.data.redis.port", "6379");
        } else {
            host = env.getProperty("spring.data.redis.host");
            port = env.getProperty("spring.data.redis.port", "6379");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("spring.data.redis.host or a host in spring.data.redis.url is required to attach Redis");
        }
        overlay.setProperty(InfrastructureDescriptor.REDIS_HOST, host);
        overlay.setProperty(InfrastructureDescriptor.REDIS_PORT, port);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static final class IsolationCustomizer implements ContextCustomizer {
        @Override public void customizeContext(org.springframework.context.ConfigurableApplicationContext context,
                                               MergedContextConfiguration merged) {
            Properties overlay = attachOverlay(context.getEnvironment());
            WorkerStore store = WorkerStore.get(overlay);
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
            if (!InfrastructureDescriptor.NONE_CACHE_BACKEND.equals(store.cacheBackend.id())) {
                values.put("spring.data.redis.host", store.redisHost());
                values.put("spring.data.redis.port", store.redisPort());
                values.put("spring.data.redis.database", store.redisDatabase);
                values.put("spring.data.redis.username", store.redisUsername());
                values.put("spring.data.redis.password", store.redisPassword());
            }
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
