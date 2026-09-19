package io.github.brody0125.springtestisolation.gradle;

import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureOverrides;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageOverrideIgnoreTest {
    @Test void unusedImageOverrideDoesNotFail() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(IsolatedTestsPlugin.class);
        var options = project.getExtensions().getByType(IsolatedTestsPlugin.Options.class);
        options.getJdbcBackend().set("mysql");
        options.getPostgresImage().set("postgres:does-not-start");
        options.getRedisImage().set("redis:does-not-start");
        assertDoesNotThrow(() -> InfrastructureOverrides.validate(options));
    }

    @Test void maxCacheSlotsStillValidated() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(IsolatedTestsPlugin.class);
        var options = project.getExtensions().getByType(IsolatedTestsPlugin.Options.class);
        options.getMaxCacheSlots().set(0);
        var error = assertThrows(org.gradle.api.GradleException.class, () -> InfrastructureOverrides.validate(options));
        assertTrue(error.getMessage().contains("maxCacheSlots"));
    }
}
