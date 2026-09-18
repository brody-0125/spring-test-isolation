package io.github.brody0125.springtestisolation;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

/** afterTestClass executes before Smart Context's order 2990 listener. */
public final class BeforeBoundaryListener extends AbstractTestExecutionListener {
    @Override public int getOrder() { return 3001; }
    @Override public void prepareTestInstance(TestContext context) { WorkerStore.healthy(); }
    @Override public void beforeTestMethod(TestContext context) throws Exception {
        WorkerStore.healthy();
        if (context.hasApplicationContext()) verifyWorkerConnections(context);
    }
    static void rejectUnsupportedTestModel(Class<?> testClass) {
        for (Class<?> type = testClass; type != null; type = type.getEnclosingClass()) {
            if (AnnotationUtils.findAnnotation(type, ContextHierarchy.class) != null) {
                throw new IllegalStateException("@ContextHierarchy is not supported with worker isolation");
            }
        }
        if (testClass.isMemberClass() && (Modifier.isStatic(testClass.getModifiers()) || declaresOwnContext(testClass))) {
            throw new IllegalStateException("@Nested test classes must inherit the enclosing class context");
        }
    }
    /** ponytail: declared context annotations only; compare MergedContextConfiguration if Nested + @TestPropertySource shows up. */
    private static boolean declaresOwnContext(Class<?> testClass) {
        var nested = testClass.getDeclaredAnnotation(NestedTestConfiguration.class);
        if (nested != null && nested.value() == NestedTestConfiguration.EnclosingConfiguration.OVERRIDE) {
            return true;
        }
        for (Annotation annotation : testClass.getDeclaredAnnotations()) {
            if (isContextAnnotation(annotation.annotationType())) return true;
        }
        return false;
    }
    private static boolean isContextAnnotation(Class<?> type) {
        return type == ContextConfiguration.class
                || type == ContextHierarchy.class
                || type.getName().equals("org.springframework.boot.test.context.SpringBootTest");
    }
    private static Class<?> rootClass(Class<?> testClass) {
        Class<?> type = testClass;
        while (type.getEnclosingClass() != null) type = type.getEnclosingClass();
        return type;
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
        Class<?> testClass = test.getTestClass();
        rejectUnsupportedTestModel(testClass);
        if (testClass.isMemberClass()) {
            synchronized (BoundaryState.class) {
                if (BoundaryState.running != rootClass(testClass)) {
                    throw new IllegalStateException("Concurrent classes inside one worker are forbidden");
                }
            }
            if (test.hasApplicationContext()) verifyWorkerConnections(test);
            return;
        }
        synchronized (BoundaryState.class) {
            if (BoundaryState.running != null) throw new IllegalStateException("Concurrent classes inside one worker are forbidden");
            BoundaryState.running = testClass;
        }
        try {
            var listeners = new TestContextManager(testClass).getTestExecutionListeners();
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
            System.out.println("PTK class-start worker=" + System.getProperty("org.gradle.test.worker") + " class=" + testClass.getName());
        } catch (Exception e) { WorkerStore.poison(e); throw e; }
    }
    @Override public void afterTestClass(TestContext test) throws Exception {
        if (test.getTestClass().isMemberClass()) return;
        try { BoundaryCalls.invoke(BoundaryCalls.Phase.QUIESCE); }
        catch (Exception e) { WorkerStore.poison(e); throw e; }
    }
}
