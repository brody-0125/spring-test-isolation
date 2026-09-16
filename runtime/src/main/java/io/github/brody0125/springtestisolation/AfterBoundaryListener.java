package io.github.brody0125.springtestisolation;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Reverse callback ordering puts this after Smart Context's close and destruction callbacks. */
public final class AfterBoundaryListener extends AbstractTestExecutionListener {
    @Override public int getOrder() { return 2980; }
    @Override public void afterTestClass(TestContext test) throws Exception {
        if (test.getTestClass().isMemberClass()) return;
        try {
            WorkerStore.healthy();
            WorkerStore.get().reset();
            BoundaryCalls.invoke(BoundaryCalls.Phase.RESET);
            System.out.println("PTK class-clean worker=" + System.getProperty("org.gradle.test.worker") + " class=" + test.getTestClass().getName());
        } catch (Exception e) { WorkerStore.poison(e); throw e; }
        finally { synchronized (BoundaryState.class) { BoundaryState.running = null; } }
    }
}
