package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerDescriptorFiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttachDescriptorTest {
    @Test void descriptorDoesNotPublishContainerEndpoints() {
        var properties = WorkerDescriptorFiles.baseProperties("postgresql", "redis", 8);
        assertEquals("postgresql", properties.getProperty(InfrastructureDescriptor.JDBC_BACKEND));
        assertEquals("redis", properties.getProperty(InfrastructureDescriptor.CACHE_BACKEND));
        assertEquals("8", properties.getProperty(InfrastructureDescriptor.CACHE_SLOTS));
        assertNotNull(properties.getProperty(InfrastructureDescriptor.RUN_ID));
        assertNull(properties.getProperty(InfrastructureDescriptor.JDBC_URL));
        assertNull(properties.getProperty(InfrastructureDescriptor.REDIS_HOST));
    }

    @Test void descriptorRejectsInvalidSlotCount() {
        assertThrows(IllegalArgumentException.class, () -> WorkerDescriptorFiles.baseProperties("postgresql", "redis", 0));
    }
}
