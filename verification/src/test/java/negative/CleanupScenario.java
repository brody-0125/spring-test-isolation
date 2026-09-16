package negative;

import io.github.brody0125.springtestisolation.ClassBoundary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@Tag("negative")
@SpringBootTest(classes = {example.TestApplication.class, CleanupScenario.BrokenCleanup.class})
abstract class CleanupScenario {
    @Test void body() { System.out.println("NEGATIVE_BODY " + getClass().getSimpleName()); }
    @TestConfiguration static class BrokenCleanup {
        @Bean ClassBoundary brokenBoundary() {
            return new ClassBoundary() {
                public void quiesce() throws Exception {
                    if (Boolean.getBoolean("fixture.timeout")) Thread.sleep(30_000);
                    throw new IllegalStateException("INJECTED_CLEANUP_FAILURE");
                }
                public void reset() {}
                public void resume() {}
            };
        }
    }
}
