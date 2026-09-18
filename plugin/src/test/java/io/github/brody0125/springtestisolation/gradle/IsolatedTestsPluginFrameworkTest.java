package io.github.brody0125.springtestisolation.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsolatedTestsPluginFrameworkTest {
    @Test void testNgParallelIsRejected() {
        var test = testTask();
        test.useTestNG();
        ((TestNGOptions) test.getOptions()).setParallel("methods");
        var error = assertThrows(GradleException.class, () -> IsolatedTestsPlugin.applyFramework(test));
        assertTrue(error.getMessage().contains("TestNG parallel"));
    }

    @Test void testNgRegistersOrderingAndGuardListeners() {
        var test = testTask();
        test.useTestNG();
        IsolatedTestsPlugin.applyFramework(test);
        var options = (TestNGOptions) test.getOptions();
        assertEquals("none", options.getParallel());
        assertEquals(1, options.getThreadCount());
        assertTrue(options.getListeners().contains(
                "com.github.seregamorph.testsmartcontext.testng.SmartDirtiesSuiteListener"));
        assertTrue(options.getListeners().contains(
                "io.github.brody0125.springtestisolation.TestNGExecutionGuard"));
    }

    @Test void junit4IsSwitchedToJUnitPlatform() {
        var test = testTask();
        test.useJUnit();
        IsolatedTestsPlugin.applyFramework(test);
        assertInstanceOf(JUnitPlatformOptions.class, test.getOptions());
        assertEquals("1", test.getSystemProperties().get("kotest.framework.parallelism"));
    }

    private static org.gradle.api.tasks.testing.Test testTask() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        return project.getTasks().named("test", org.gradle.api.tasks.testing.Test.class).get();
    }
}
