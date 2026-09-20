package io.github.brody0125.springtestisolation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Build-owned worker descriptor file shared by the Gradle and Maven integrations. */
public final class WorkerDescriptorFiles {
    private WorkerDescriptorFiles() {}

    public static Path writeBase(Path directory, String jdbcBackend, String cacheBackend, int slots) throws IOException {
        return write(directory, baseProperties(jdbcBackend, cacheBackend, slots));
    }

    public static Properties baseProperties(String jdbcBackend, String cacheBackend, int slots) {
        if (slots < 1 || slots > 255) throw new IllegalArgumentException("maxCacheSlots must be between 1 and 255");
        Properties p = new Properties();
        p.setProperty(InfrastructureDescriptor.RUN_ID, UUID.randomUUID().toString());
        p.setProperty(InfrastructureDescriptor.JDBC_BACKEND, jdbcBackend);
        p.setProperty(InfrastructureDescriptor.CACHE_BACKEND, cacheBackend);
        p.setProperty(InfrastructureDescriptor.CACHE_SLOTS, Integer.toString(slots));
        return p;
    }

    public static Path write(Path directory, Properties properties) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("connection.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "Worker isolation descriptor");
        }
        return file;
    }
}
