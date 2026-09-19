package io.github.brody0125.springtestisolation.gradle;

import org.gradle.api.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.process.CommandLineArgumentProvider;
import io.github.brody0125.springtestisolation.gradle.infrastructure.InfrastructureProviders;
import java.util.List;

public class IsolatedTestsPlugin implements Plugin<Project> {
    private static final String ORDERER = "com.github.seregamorph.testsmartcontext.jupiter.SmartDirtiesClassOrderer";
    public static class ValidateSettings implements Action<Task> {
        @Override public void execute(Task task) {
            validateFramework((Test) task);
        }
    }
    static void applyFramework(Test test) {
        if (test.getOptions() instanceof TestNGOptions testng) {
            configureTestNG(testng);
            return;
        }
        if (!(test.getOptions() instanceof JUnitPlatformOptions)) test.useJUnitPlatform();
        test.systemProperty("junit.jupiter.execution.parallel.enabled", "false");
        test.systemProperty("junit.jupiter.extensions.autodetection.enabled", "true");
        test.systemProperty("junit.jupiter.testclass.order.default", ORDERER);
        test.systemProperty("kotest.framework.parallelism", "1");
    }
    static void configureTestNG(TestNGOptions options) {
        validateTestNG(options);
        options.setParallel("none");
        options.setThreadCount(1);
    }
    static void validateFramework(Test test) {
        if (test.getOptions() instanceof TestNGOptions testng) {
            validateTestNG(testng);
            return;
        }
        if (!(test.getOptions() instanceof JUnitPlatformOptions))
            throw new GradleException("Only JUnit Platform and TestNG are supported with worker isolation");
        java.util.Map<String, String> required = java.util.Map.of(
                "junit.jupiter.execution.parallel.enabled", "false",
                "junit.jupiter.extensions.autodetection.enabled", "true",
                "junit.jupiter.testclass.order.default", ORDERER,
                "kotest.framework.parallelism", "1");
        required.forEach((key, value) -> {
            if (!value.equals(String.valueOf(test.getSystemProperties().get(key))))
                throw new GradleException("Incompatible test setting: " + key + " must be " + value);
            for (String argument : test.getJvmArgs()) {
                if (argument.startsWith("-D" + key + "=") && !argument.equals("-D" + key + "=" + value))
                    throw new GradleException("Incompatible JVM argument: " + argument);
            }
        });
    }
    static void validateTestNG(TestNGOptions options) {
        String parallel = options.getParallel();
        if (parallel != null && !parallel.isBlank() && !"none".equalsIgnoreCase(parallel)
                && !"false".equalsIgnoreCase(parallel))
            throw new GradleException("TestNG parallel execution must be disabled inside each Gradle worker");
        if (options.getThreadCount() > 1)
            throw new GradleException("TestNG threadCount must be 1 inside each Gradle worker");
        if (!options.getSuiteXmlFiles().isEmpty())
            throw new GradleException("TestNG suite XML is unsupported with worker isolation");
    }
    static void validateSlots(Integer slots) {
        if (slots != null && (slots < 1 || slots > 255)) {
            throw new GradleException("maxCacheSlots must be between 1 and 255");
        }
    }
    public abstract static class Options {
        public abstract Property<Integer> getWorkers();
        public abstract Property<String> getJdbcBackend();
        public abstract Property<String> getCacheBackend();
        public abstract Property<String> getPostgresImage();
        public abstract Property<String> getMysqlImage();
        public abstract Property<String> getOracleImage();
        public abstract Property<String> getRedisImage();
        public abstract Property<Integer> getRedisLogicalDatabases();
        public abstract Property<Integer> getMaxCacheSlots();
    }
    public static class ConnectionArguments implements CommandLineArgumentProvider {
        private final Provider<Containers> service;
        public ConnectionArguments(Provider<Containers> service) { this.service = service; }
        @Internal public Provider<Containers> getService() { return service; }
        @Override public Iterable<String> asArguments() {
            return List.of("-Dspringtestisolation.descriptor=" + service.get().descriptor());
        }
    }
    @Override public void apply(Project project) {
        Options options = project.getExtensions().create("isolatedTests", Options.class);
        options.getWorkers().convention(2);
        options.getJdbcBackend().convention("postgresql");
        options.getCacheBackend().convention("redis");
        Provider<Containers> service = project.getGradle().getSharedServices()
                .registerIfAbsent("spring-test-isolation-containers", Containers.class, spec -> {
                    spec.getParameters().getJdbcBackend().set(options.getJdbcBackend());
                    spec.getParameters().getCacheBackend().set(options.getCacheBackend());
                    spec.getParameters().getMaxCacheSlots().set(options.getMaxCacheSlots());
                });
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.systemProperty("springtestisolation.task", test.getPath());
            test.usesService(service);
            test.getJvmArgumentProviders().add(new ConnectionArguments(service));
            test.systemProperty("junit.jupiter.execution.parallel.enabled", "false");
            test.systemProperty("junit.jupiter.extensions.autodetection.enabled", "true");
            test.systemProperty("junit.jupiter.testclass.order.default",
                    ORDERER);
            test.systemProperty("kotest.framework.parallelism", "1");
            test.setForkEvery(0);
            test.doFirst(new ValidateSettings());
        });
        project.afterEvaluate(p -> {
            int workers = options.getWorkers().get();
            if (workers < 1 || workers > 255) throw new GradleException("workers must be between 1 and 255");
            try {
                InfrastructureProviders.jdbc(options.getJdbcBackend().get());
                InfrastructureProviders.cache(options.getCacheBackend().get());
                validateSlots(options.getMaxCacheSlots().getOrNull());
            } catch (IllegalArgumentException e) {
                throw new GradleException(e.getMessage(), e);
            }
            p.getTasks().withType(Test.class).configureEach(test -> {
                test.setMaxParallelForks(workers);
                applyFramework(test);
            });
        });
    }
}
