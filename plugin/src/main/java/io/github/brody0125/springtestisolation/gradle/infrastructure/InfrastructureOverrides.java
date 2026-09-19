package io.github.brody0125.springtestisolation.gradle.infrastructure;

import io.github.brody0125.springtestisolation.gradle.IsolatedTestsPlugin;
import org.gradle.api.GradleException;

public final class InfrastructureOverrides {
    private InfrastructureOverrides() {}

    public static void validate(IsolatedTestsPlugin.Options options) {
        Integer slots = options.getMaxCacheSlots().getOrNull();
        if (slots != null && (slots < 1 || slots > 255)) {
            throw new GradleException("maxCacheSlots must be between 1 and 255");
        }
    }
}
