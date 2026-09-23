package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerDescriptorFiles;
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

    public synchronized Path descriptor() {
        if (closed) throw new IllegalStateException("Infrastructure service has already closed");
        if (descriptor != null) return descriptor;
        try {
            String jdbcId = getParameters().getJdbcBackend().getOrElse(InfrastructureDescriptor.DEFAULT_JDBC_BACKEND);
            String cacheId = getParameters().getCacheBackend().getOrElse(InfrastructureDescriptor.DEFAULT_CACHE_BACKEND);
            int slots = getParameters().getMaxCacheSlots().getOrElse(
                    Integer.parseInt(InfrastructureVersions.MAX_WORKER_SLOTS));
            Path directory = Files.createTempDirectory("spring-test-isolation-");
            descriptor = WorkerDescriptorFiles.writeBase(directory, jdbcId, cacheId, slots);
            return descriptor;
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
