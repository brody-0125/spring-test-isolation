package io.github.brody0125.springtestisolation.gradle;

import org.gradle.api.GradleException;
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
        IsolatedTestsPlugin.validateSlots(options.getMaxCacheSlots().getOrNull());
    }

    @Test void maxCacheSlotsStillValidated() {
        var error = assertThrows(GradleException.class, () -> IsolatedTestsPlugin.validateSlots(0));
        assertTrue(error.getMessage().contains("maxCacheSlots"));
    }
}
