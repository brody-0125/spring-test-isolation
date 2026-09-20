package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerDescriptorFilesTest {
    @Test
    void writesBaseDescriptorProperties() throws IOException {
        var directory = Files.createTempDirectory("ptk-descriptor");
        Properties properties = WorkerDescriptorFiles.baseProperties("postgresql", "redis", 4);
        var file = WorkerDescriptorFiles.write(directory, properties);
        Properties loaded = new Properties();
        try (var in = Files.newInputStream(file)) {
            loaded.load(in);
        }
        assertEquals("postgresql", loaded.getProperty(InfrastructureDescriptor.JDBC_BACKEND));
        assertEquals("redis", loaded.getProperty(InfrastructureDescriptor.CACHE_BACKEND));
        assertEquals("4", loaded.getProperty(InfrastructureDescriptor.CACHE_SLOTS));
        assertTrue(loaded.getProperty(InfrastructureDescriptor.RUN_ID).length() > 8);
    }
}
