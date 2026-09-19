package io.github.brody0125.springtestisolation.gradle;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsolatedTestsPluginSlotsTest {
    @Test void maxCacheSlotsMustBeInRange() {
        var error = assertThrows(GradleException.class, () -> IsolatedTestsPlugin.validateSlots(0));
        assertTrue(error.getMessage().contains("maxCacheSlots"));
        IsolatedTestsPlugin.validateSlots(null);
        IsolatedTestsPlugin.validateSlots(255);
    }
}
