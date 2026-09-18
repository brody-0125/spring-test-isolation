package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.NestedTestConfiguration;
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
        assertDoesNotThrow(() -> BeforeBoundaryListener.rejectUnsupportedTestModel(InheritCase.InheritNested.class));
        assertThrows(IllegalStateException.class, () -> BeforeBoundaryListener.rejectUnsupportedTestModel(MemberCase.NestedMember.class));
        assertThrows(IllegalStateException.class, () -> BeforeBoundaryListener.rejectUnsupportedTestModel(OwnContextCase.OwnNested.class));
        assertThrows(IllegalStateException.class, () -> BeforeBoundaryListener.rejectUnsupportedTestModel(OverrideCase.OverrideNested.class));
        assertThrows(IllegalStateException.class,
                () -> BeforeBoundaryListener.rejectUnsupportedTestModel(ContextHierarchyCase.class));
        assertThrows(IllegalStateException.class,
                () -> BeforeBoundaryListener.rejectUnsupportedTestModel(HierarchyEnclosing.Nested.class));
    }
    @ContextHierarchy(@ContextConfiguration(classes = String.class))
    static class ContextHierarchyCase {}
    static class MemberCase { static class NestedMember {} }
    static class InheritCase { class InheritNested {} }
    static class OwnContextCase {
        @ContextConfiguration(classes = String.class)
        class OwnNested {}
    }
    static class OverrideCase {
        @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
        class OverrideNested {}
    }
    @ContextHierarchy(@ContextConfiguration(classes = String.class))
    static class HierarchyEnclosing { class Nested {} }
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
