package io.github.brody0125.springtestisolation;

import org.junit.platform.launcher.*;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.platform.commons.support.AnnotationSupport;

/** Launcher listener exceptions alone can be logged and swallowed; poison is checked by Spring callbacks. */
public final class ExecutionGuard implements TestExecutionListener {
    @Override public void testPlanExecutionStarted(TestPlan plan) {
        if (System.getProperty("springtestisolation.descriptor") == null) return;
        try {
            var parameters = plan.getConfigurationParameters();
            if (parameters.getBoolean("junit.jupiter.execution.parallel.enabled").orElse(false))
                throw new IllegalStateException("JUnit parallel execution must be disabled inside each Gradle worker");
            if (!parameters.getBoolean("junit.jupiter.extensions.autodetection.enabled").orElse(false))
                throw new IllegalStateException("WorkerGuardExtension autodetection must remain enabled");
            if (!parameters.get("junit.jupiter.testclass.order.default").orElse("").equals(
                    "com.github.seregamorph.testsmartcontext.jupiter.SmartDirtiesClassOrderer"))
                throw new IllegalStateException("SmartDirtiesClassOrderer is required");
            for (TestIdentifier root : plan.getRoots()) for (TestIdentifier node : plan.getDescendants(root)) {
                var source = node.getSource().orElse(null);
                java.lang.reflect.AnnotatedElement element = source instanceof ClassSource cls ? cls.getJavaClass()
                        : source instanceof MethodSource method ? method.getJavaMethod() : null;
                if (element != null && AnnotationSupport.findAnnotation(element, Execution.class)
                        .map(a -> a.value() == ExecutionMode.CONCURRENT).orElse(false))
                    throw new IllegalStateException("@Execution(CONCURRENT) is incompatible: " + node.getDisplayName());
                if (element != null && AnnotationSupport.findAnnotation(element, TestClassOrder.class)
                        .map(a -> !a.value().getName().equals("com.github.seregamorph.testsmartcontext.jupiter.SmartDirtiesClassOrderer")).orElse(false))
                    throw new IllegalStateException("Custom @TestClassOrder is incompatible: " + node.getDisplayName());
            }
        } catch (Exception e) { WorkerStore.poison(e); }
    }
}
