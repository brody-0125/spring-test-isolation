package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.Test;
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
}
