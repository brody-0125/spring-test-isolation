package io.github.brody0125.springtestisolation;

import org.testng.*;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/** Rejects TestNG parallelism inside a Gradle worker; poison is checked by Spring callbacks. */
public final class TestNGExecutionGuard implements ISuiteListener, IInvokedMethodListener {
    @Override public void onStart(ISuite suite) {
        if (System.getProperty("springtestisolation.descriptor") == null) return;
        try {
            XmlSuite xml = suite.getXmlSuite();
            rejectParallel(xml.getParallel());
            for (XmlTest test : xml.getTests()) rejectParallel(test.getParallel());
        } catch (RuntimeException e) { WorkerStore.poison(e); throw e; }
    }
    @Override public void onFinish(ISuite suite) {}
    @Override public void beforeInvocation(IInvokedMethod method, ITestResult result) {
        if (System.getProperty("springtestisolation.descriptor") == null) return;
        try {
            if (method.getTestMethod().getThreadPoolSize() > 1)
                throw new IllegalStateException("TestNG threadPoolSize must be 1 inside each Gradle worker");
        } catch (RuntimeException e) { WorkerStore.poison(e); throw e; }
    }
    @Override public void afterInvocation(IInvokedMethod method, ITestResult result) {}
    private static void rejectParallel(XmlSuite.ParallelMode mode) {
        if (mode != null && mode.isParallel())
            throw new IllegalStateException("TestNG parallel execution must be disabled inside each Gradle worker");
    }
}
