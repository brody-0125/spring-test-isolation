package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.gradle.infrastructure.CacheInfrastructureProvider;
import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureProviders;
import io.github.brody0125.springtestisolation.gradle.infrastructure.JdbcInfrastructureProvider;
import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureConfiguration;
import io.github.brody0125.springtestisolation.gradle.infrastructure.StartedInfrastructure;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import java.nio.file.*;
import java.util.*;

/** Build-owned resources. No Spring context owns these containers. */
public abstract class Containers implements BuildService<Containers.Parameters>, AutoCloseable {
    public interface Parameters extends BuildServiceParameters {
        Property<String> getJdbcBackend();
        Property<String> getCacheBackend();
        Property<String> getPostgresImage();
        Property<String> getRedisImage();
        Property<Integer> getRedisLogicalDatabases();
        Property<Integer> getMaxCacheSlots();
    }

    private StartedInfrastructure jdbc;
    private StartedInfrastructure cache;
    private Path descriptor;
    private boolean closed;

    public synchronized Path descriptor() {
        if (closed) throw new IllegalStateException("Infrastructure service has already closed");
        if (descriptor != null) return descriptor;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Containers.class.getClassLoader());
        try {
            String jdbcId = getParameters().getJdbcBackend().getOrElse("postgresql");
            String cacheId = getParameters().getCacheBackend().getOrElse("redis");
            InfrastructureConfiguration configuration = InfrastructureConfiguration.resolve(
                    getParameters().getPostgresImage(),
                    getParameters().getRedisImage(),
                    getParameters().getRedisLogicalDatabases(),
                    getParameters().getMaxCacheSlots());
            JdbcInfrastructureProvider jdbcProvider = InfrastructureProviders.jdbc(jdbcId);
            CacheInfrastructureProvider cacheProvider = InfrastructureProviders.cache(cacheId);
            jdbc = jdbcProvider.start(configuration);
            cache = cacheProvider.start(configuration);
            Path directory = Files.createTempDirectory("spring-test-isolation-");
            Properties p = new Properties();
            p.setProperty("run", UUID.randomUUID().toString());
            p.setProperty("jdbc.backend", jdbcId);
            p.setProperty("cache.backend", cacheId);
            jdbc.publish(p);
            cache.publish(p);
            System.out.println("PTK infrastructure-start jdbc=" + jdbc.containerId() + " cache=" + cache.containerId()
                    + " run=" + p.getProperty("run") + " jdbc.backend=" + jdbcId + " cache.backend=" + cacheId);
            Path file = directory.resolve("connection.properties");
            descriptor = file;
            try (var out = Files.newOutputStream(file)) { p.store(out, "Ephemeral test infrastructure"); }
            return file;
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Cannot start shared test infrastructure", e);
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    /** First infrastructure shutdown failure stays primary; later steps attach as suppressed. */
    static RuntimeException combineCloseFailures(RuntimeException first, RuntimeException second) {
        if (second == null) return first;
        if (first == null) return second;
        first.addSuppressed(second);
        return first;
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try { if (cache != null) cache.stop(); } catch (Exception e) { failure = new RuntimeException(e); }
        try { if (jdbc != null) jdbc.stop(); } catch (Exception e) { failure = combineCloseFailures(failure, new RuntimeException(e)); }
        if (descriptor != null) {
            try (var paths = Files.walk(descriptor.getParent())) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (Exception e) {
                failure = combineCloseFailures(failure, new IllegalStateException("Cannot remove infrastructure descriptor", e));
            }
        }
        if (failure != null) throw failure;
        System.out.println("PTK infrastructure-closed");
    }
}
