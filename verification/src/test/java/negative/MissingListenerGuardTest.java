package negative;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@Tag("guard")
@SpringBootTest(classes = example.TestApplication.class)
@TestExecutionListeners(listeners = {}, inheritListeners = false,
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class MissingListenerGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_MISSING_LISTENER_BODY"); }
}
