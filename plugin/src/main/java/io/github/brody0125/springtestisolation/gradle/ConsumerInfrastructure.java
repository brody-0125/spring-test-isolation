package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureConfiguration;
import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureProviders;
import io.github.brody0125.springtestisolation.gradle.infrastructure.StartedInfrastructure;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;
import java.util.List;
import java.util.Properties;

/** Consumer-owned containers for this repository's verification fixtures. The plugin does not register this service. */
public abstract class ConsumerInfrastructure implements BuildService<ConsumerInfrastructure.Parameters>, AutoCloseable {
    public interface Parameters extends BuildServiceParameters {
        Property<String> getJdbcBackend();
        Property<String> getPostgresImage();
        Property<String> getMysqlImage();
        Property<String> getOracleImage();
        Property<String> getRedisImage();
        Property<Integer> getRedisLogicalDatabases();
        Property<Integer> getMaxCacheSlots();
    }

    private StartedInfrastructure jdbc;
    private StartedInfrastructure cache;
    private Properties connections;
    private boolean closed;

    public synchronized Properties connections() {
        if (closed) throw new IllegalStateException("Consumer infrastructure has already closed");
        if (connections != null) return connections;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ConsumerInfrastructure.class.getClassLoader());
        try {
            String jdbcId = getParameters().getJdbcBackend().getOrElse("postgresql");
            InfrastructureConfiguration configuration = InfrastructureConfiguration.resolve(
                    getParameters().getPostgresImage(),
                    getParameters().getMysqlImage(),
                    getParameters().getOracleImage(),
                    getParameters().getRedisImage(),
                    getParameters().getRedisLogicalDatabases(),
                    getParameters().getMaxCacheSlots());
            jdbc = InfrastructureProviders.jdbc(jdbcId).start(configuration);
            cache = InfrastructureProviders.cache("redis").start(configuration);
            Properties p = new Properties();
            p.setProperty("run", "consumer");
            jdbc.publish(p);
            cache.publish(p);
            System.out.println("PTK infrastructure-start jdbc=" + jdbc.containerId() + " cache=" + cache.containerId()
                    + " run=" + p.getProperty("run") + " jdbc.backend=" + jdbcId + " cache.backend=redis");
            connections = p;
            return p;
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Cannot start consumer test infrastructure", e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try { if (cache != null) cache.stop(); } catch (Exception e) { failure = new RuntimeException(e); }
        try { if (jdbc != null) jdbc.stop(); } catch (Exception e) {
            if (failure == null) failure = new RuntimeException(e);
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
        System.out.println("PTK infrastructure-closed");
    }

    public static void install(Project project) {
        IsolatedTestsPlugin.Options options = project.getExtensions().getByType(IsolatedTestsPlugin.Options.class);
        Provider<ConsumerInfrastructure> infra = project.getGradle().getSharedServices()
                .registerIfAbsent("consumer-infra", ConsumerInfrastructure.class, spec -> {
                    spec.getParameters().getJdbcBackend().set(options.getJdbcBackend());
                    spec.getParameters().getPostgresImage().set(options.getPostgresImage());
                    spec.getParameters().getMysqlImage().set(options.getMysqlImage());
                    spec.getParameters().getOracleImage().set(options.getOracleImage());
                    spec.getParameters().getRedisImage().set(options.getRedisImage());
                    spec.getParameters().getRedisLogicalDatabases().set(options.getRedisLogicalDatabases());
                    spec.getParameters().getMaxCacheSlots().set(options.getMaxCacheSlots());
                });
        project.getTasks().withType(Test.class).configureEach(test -> {
            if ("jdbcTcTest".equals(test.getName())) return;
            test.usesService(infra);
            test.getJvmArgumentProviders().add(new ConnectionArguments(infra));
        });
    }

    public static final class ConnectionArguments implements CommandLineArgumentProvider {
        private final Provider<ConsumerInfrastructure> service;
        public ConnectionArguments(Provider<ConsumerInfrastructure> service) { this.service = service; }
        @Internal public Provider<ConsumerInfrastructure> getService() { return service; }
        @Override public Iterable<String> asArguments() {
            Properties p = service.get().connections();
            return List.of(
                    "-Dspring.datasource.url=" + p.getProperty("jdbc"),
                    "-Dspring.datasource.username=" + p.getProperty("user"),
                    "-Dspring.datasource.password=" + p.getProperty("password"),
                    "-Dspring.data.redis.host=" + p.getProperty("redis.host"),
                    "-Dspring.data.redis.port=" + p.getProperty("redis.port"));
        }
    }
}
