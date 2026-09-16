package negative;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.*;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("guard")
@Execution(ExecutionMode.CONCURRENT)
@SpringBootTest(classes = example.TestApplication.class)
class ConcurrentGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_GUARD_BODY_EXECUTED"); }
}
