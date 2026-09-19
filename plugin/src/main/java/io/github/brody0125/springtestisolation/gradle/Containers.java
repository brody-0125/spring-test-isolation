package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureVersions;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import java.nio.file.*;
import java.util.*;

/** Build-owned descriptor directory. */
public abstract class Containers implements BuildService<Containers.Parameters>, AutoCloseable {
    public interface Parameters extends BuildServiceParameters {
        Property<String> getJdbcBackend();
        Property<String> getCacheBackend();
        Property<Integer> getMaxCacheSlots();
    }

    private Path descriptor;
    private boolean closed;

    static Properties descriptorProperties(String jdbcBackend, String cacheBackend, int slots) {
        if (slots < 1 || slots > 255) throw new IllegalArgumentException("maxCacheSlots must be between 1 and 255");
        Properties p = new Properties();
        p.setProperty("run", UUID.randomUUID().toString());
        p.setProperty("jdbc.backend", jdbcBackend);
        p.setProperty("cache.backend", cacheBackend);
        p.setProperty("slots", Integer.toString(slots));
        return p;
    }

    public synchronized Path descriptor() {
        if (closed) throw new IllegalStateException("Infrastructure service has already closed");
        if (descriptor != null) return descriptor;
        try {
            String jdbcId = getParameters().getJdbcBackend().getOrElse("postgresql");
            String cacheId = getParameters().getCacheBackend().getOrElse("redis");
            int slots = getParameters().getMaxCacheSlots().getOrElse(
                    Integer.parseInt(InfrastructureVersions.MAX_WORKER_SLOTS));
            Properties p = descriptorProperties(jdbcId, cacheId, slots);
            Path directory = Files.createTempDirectory("spring-test-isolation-");
            Path file = directory.resolve("connection.properties");
            descriptor = file;
            try (var out = Files.newOutputStream(file)) { p.store(out, "Worker isolation descriptor"); }
            return file;
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Cannot create worker isolation descriptor", e);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (descriptor == null) return;
        try (var paths = Files.walk(descriptor.getParent())) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot remove infrastructure descriptor", e);
        }
    }
}
