package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Remains effective even if a test replaces Spring's default listener list. */
public final class WorkerGuardExtension implements BeforeAllCallback, BeforeEachCallback {
    @Override public void beforeAll(ExtensionContext context) {
        WorkerStore.healthy();
        boolean spring = AnnotationSupport.findRepeatableAnnotations(context.getRequiredTestClass(), ExtendWith.class)
                .stream().flatMap(a -> java.util.Arrays.stream(a.value())).anyMatch(SpringExtension.class::equals);
        if (!spring) return;
        try {
            var listeners = new TestContextManager(context.getRequiredTestClass()).getTestExecutionListeners();
            for (Class<?> required : new Class<?>[]{BeforeBoundaryListener.class, AfterBoundaryListener.class,
                    com.github.seregamorph.testsmartcontext.SmartDirtiesContextTestExecutionListener.class}) {
                if (listeners.stream().noneMatch(required::isInstance))
                    throw new IllegalStateException("Required listener missing: " + required.getName());
            }
        } catch (RuntimeException failure) { WorkerStore.poison(failure); throw failure; }
    }
    @Override public void beforeEach(ExtensionContext context) { WorkerStore.healthy(); }
}
