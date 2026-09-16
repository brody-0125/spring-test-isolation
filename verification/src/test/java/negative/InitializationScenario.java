package negative;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@Tag("guard")
@SpringBootTest(classes = {example.TestApplication.class, InitializationScenario.BrokenStartup.class})
abstract class InitializationScenario {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_INITIALIZATION_BODY"); }
    @TestConfiguration static class BrokenStartup {
        @Bean String deliberatelyBroken() { throw new IllegalStateException("INJECTED_INITIALIZATION_FAILURE"); }
    }
}
