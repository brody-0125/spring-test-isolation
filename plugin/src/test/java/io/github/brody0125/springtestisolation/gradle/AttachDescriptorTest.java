package io.github.brody0125.springtestisolation.gradle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttachDescriptorTest {
    @Test void descriptorDoesNotPublishContainerEndpoints() {
        var properties = Containers.descriptorProperties("postgresql", "redis", 8);
        assertEquals("postgresql", properties.getProperty("jdbc.backend"));
        assertEquals("redis", properties.getProperty("cache.backend"));
        assertEquals("8", properties.getProperty("slots"));
        assertNotNull(properties.getProperty("run"));
        assertNull(properties.getProperty("jdbc"));
        assertNull(properties.getProperty("redis.host"));
    }

    @Test void descriptorRejectsInvalidSlotCount() {
        assertThrows(IllegalArgumentException.class, () -> Containers.descriptorProperties("postgresql", "redis", 0));
    }
}
