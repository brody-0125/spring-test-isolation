package io.github.brody0125.springtestisolation.gradle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainersCloseFailuresTest {
    @Test void closeFailuresKeepEarlierStepPrimary() {
        var redis = new RuntimeException("redis stop failed");
        var postgres = new RuntimeException("postgres stop failed");
        var combined = Containers.combineCloseFailures(redis, postgres);
        assertSame(redis, combined);
        assertEquals(1, combined.getSuppressed().length);
        assertSame(postgres, combined.getSuppressed()[0]);
        assertSame(postgres, Containers.combineCloseFailures(null, postgres));
        assertSame(redis, Containers.combineCloseFailures(redis, null));
    }
}
