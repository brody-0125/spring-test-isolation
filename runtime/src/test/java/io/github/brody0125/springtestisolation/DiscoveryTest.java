package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryTest {
    @Test void repeatedDiscoveryIsPureAndDoesNotPartitionClasses() {
        var factory = new IsolationCustomizerFactory();
        var first = factory.createContextCustomizer(String.class, List.of());
        var second = factory.createContextCustomizer(Integer.class, List.of());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNull(System.getProperty("springtestisolation.descriptor"));
    }
    @Test void quotesSqlIdentifiersRatherThanInterpolatingUnescapedNames() {
        assertEquals("\"a\"\"b\"", WorkerStore.quote("a\"b"));
    }
    @Test void rejectsUnsupportedTestModels() {
        assertThrows(IllegalStateException.class, () -> BeforeBoundaryListener.rejectUnsupportedTestModel(MemberCase.NestedMember.class));
        assertThrows(IllegalStateException.class,
                () -> BeforeBoundaryListener.rejectUnsupportedTestModel(ContextHierarchyCase.class));
    }
    @ContextHierarchy(@ContextConfiguration(classes = String.class))
    static class ContextHierarchyCase {}
    static class MemberCase { static class NestedMember {} }
    @Test void cleanupFailuresKeepEarlierStepPrimary() {
        var drop = new java.sql.SQLException("drop failed");
        var redis = new RuntimeException("redis failed");
        var combined = WorkerStore.combineCleanupFailures(drop, redis);
        assertSame(drop, combined);
        assertEquals(1, combined.getSuppressed().length);
        assertSame(redis, combined.getSuppressed()[0]);
        assertSame(redis, WorkerStore.combineCleanupFailures(null, redis));
        assertSame(drop, WorkerStore.combineCleanupFailures(drop, null));
    }
}
