package io.github.brody0125.springtestisolation;

import java.util.Map;

/** JUnit Platform / Kotest settings required inside each worker JVM (Gradle worker or Surefire fork). */
public final class WorkerFrameworkSettings {
    public static final String SMART_CONTEXT_CLASS_ORDERER =
            "com.github.seregamorph.testsmartcontext.jupiter.SmartDirtiesClassOrderer";

    private WorkerFrameworkSettings() {}

    public static Map<String, String> junitPlatformSystemProperties() {
        return Map.of(
                "junit.jupiter.execution.parallel.enabled", "false",
                "junit.jupiter.extensions.autodetection.enabled", "true",
                "junit.jupiter.testclass.order.default", SMART_CONTEXT_CLASS_ORDERER,
                "kotest.framework.parallelism", "1");
    }
}
