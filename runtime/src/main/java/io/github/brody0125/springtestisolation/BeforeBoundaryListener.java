package io.github.brody0125.springtestisolation;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import javax.sql.DataSource;

/** afterTestClass executes before Smart Context's order 2990 listener. */
public final class BeforeBoundaryListener extends AbstractTestExecutionListener {
    @Override public int getOrder() { return 3001; }
    @Override public void prepareTestInstance(TestContext context) { WorkerStore.healthy(); }
    @Override public void beforeTestMethod(TestContext context) throws Exception {
        WorkerStore.healthy();
        if (context.hasApplicationContext()) verifyWorkerConnections(context);
    }
    static void rejectUnsupportedTestModel(Class<?> testClass) {
        if (testClass.isMemberClass()) {
            throw new IllegalStateException("@Nested test classes are not supported; use top-level classes with Smart Context ordering");
        }
        if (AnnotationUtils.findAnnotation(testClass, ContextHierarchy.class) != null) {
            throw new IllegalStateException("@ContextHierarchy is not supported with worker isolation");
        }
    }
    static void verifyWorkerConnections(TestContext test) throws Exception {
        var ctx = (ConfigurableApplicationContext) test.getApplicationContext();
        WorkerStore store = WorkerStore.get();
        for (DataSource source : ctx.getBeansOfType(DataSource.class).values()) ConnectionVerifier.verify(source, store);
        for (RedisConnectionFactory source : ctx.getBeansOfType(RedisConnectionFactory.class).values()) {
            ConnectionVerifier.verify(source, store);
        }
    }
    @Override public void beforeTestClass(TestContext test) throws Exception {
        WorkerStore.healthy();
        rejectUnsupportedTestModel(test.getTestClass());
        synchronized (BoundaryState.class) {
            if (BoundaryState.running != null) throw new IllegalStateException("Concurrent classes inside one worker are forbidden");
            BoundaryState.running = test.getTestClass();
        }
        try {
            var listeners = new TestContextManager(test.getTestClass()).getTestExecutionListeners();
            for (Class<?> expected : new Class<?>[]{BeforeBoundaryListener.class, AfterBoundaryListener.class,
                    com.github.seregamorph.testsmartcontext.SmartDirtiesContextTestExecutionListener.class}) {
                if (listeners.stream().noneMatch(expected::isInstance))
                    throw new IllegalStateException("Required listener missing: " + expected.getName());
            }
            var ctx = (ConfigurableApplicationContext) test.getApplicationContext();
            BoundaryState.contexts.add(ctx);
            System.out.println("PTK active-contexts worker=" + System.getProperty("org.gradle.test.worker")
                    + " count=" + BoundaryState.contexts.stream().filter(ConfigurableApplicationContext::isActive).count());
            verifyWorkerConnections(test);
            BoundaryCalls.invoke(BoundaryCalls.Phase.RESUME);
            System.out.println("PTK class-start worker=" + System.getProperty("org.gradle.test.worker") + " class=" + test.getTestClass().getName());
        } catch (Exception e) { WorkerStore.poison(e); throw e; }
    }
    @Override public void afterTestClass(TestContext test) throws Exception {
        if (test.getTestClass().isMemberClass()) return;
        try { BoundaryCalls.invoke(BoundaryCalls.Phase.QUIESCE); }
        catch (Exception e) { WorkerStore.poison(e); throw e; }
    }
}
