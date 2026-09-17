package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureConfigurationTest {
    @Test void defaultsMatchGeneratedVersions() {
        var defaults = InfrastructureConfiguration.defaults();
        assertEquals(InfrastructureVersions.POSTGRES_IMAGE, defaults.postgresImage());
        assertEquals(InfrastructureVersions.REDIS_IMAGE, defaults.redisImage());
    }

    @Test void resolveUsesDefaultsWhenUnset() {
        var project = ProjectBuilder.builder().build();
        var postgres = project.getObjects().property(String.class);
        var redis = project.getObjects().property(String.class);
        var databases = project.getObjects().property(Integer.class);
        var slots = project.getObjects().property(Integer.class);
        var resolved = InfrastructureConfiguration.resolve(postgres, redis, databases, slots);
        assertEquals(InfrastructureConfiguration.defaults().postgresImage(), resolved.postgresImage());
        assertEquals(InfrastructureConfiguration.defaults().maxCacheSlots(), resolved.maxCacheSlots());
    }

    @Test void resolveRejectsSlotsAboveDatabaseCount() {
        var project = ProjectBuilder.builder().build();
        var postgres = project.getObjects().property(String.class);
        var redis = project.getObjects().property(String.class);
        var databases = project.getObjects().property(Integer.class);
        databases.set(8);
        var slots = project.getObjects().property(Integer.class);
        slots.set(16);
        assertThrows(IllegalArgumentException.class,
                () -> InfrastructureConfiguration.resolve(postgres, redis, databases, slots));
    }
}
