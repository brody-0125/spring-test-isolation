package negative;

import example.TestApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;

@Tag("guard")
@ContextHierarchy(@ContextConfiguration(classes = TestApplication.class))
@SpringBootTest
class ContextHierarchyGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_CONTEXT_HIERARCHY_BODY"); }
}
