package negative;

import example.TestApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("guard")
@SpringBootTest(classes = TestApplication.class)
class NestedGuardTest {
    @Nested
    @SpringBootTest(classes = TestApplication.class)
    class NestedCase {
        @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_NESTED_BODY"); }
    }
}
