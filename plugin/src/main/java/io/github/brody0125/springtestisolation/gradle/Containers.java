package io.github.brody0125.springtestisolation.gradle;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.*;
import java.util.*;

/** Build-owned resources. No Spring context owns these containers. */
public abstract class Containers implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private PostgreSQLContainer postgres;
    private GenericContainer<?> redis;
    private Path descriptor;
    private boolean closed;

    public synchronized Path descriptor() {
        if (closed) throw new IllegalStateException("Infrastructure service has already closed");
        if (descriptor != null) return descriptor;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Containers.class.getClassLoader());
        try {
            postgres = new PostgreSQLContainer("postgres:16.9-alpine");
            redis = new GenericContainer<>("redis:7.4.4-alpine").withExposedPorts(6379)
                    .withCommand("redis-server", "--databases", "256");
            postgres.start();
            redis.start();
            Path directory = Files.createTempDirectory("spring-test-isolation-");
            Properties p = new Properties();
            p.setProperty("run", UUID.randomUUID().toString());
            System.out.println("PTK infrastructure-start postgres=" + postgres.getContainerId() + " redis=" + redis.getContainerId()
                    + " run=" + p.getProperty("run"));
            p.setProperty("jdbc", postgres.getJdbcUrl());
            p.setProperty("user", postgres.getUsername());
            p.setProperty("password", postgres.getPassword());
            p.setProperty("redis.host", redis.getHost());
            p.setProperty("redis.port", redis.getMappedPort(6379).toString());
            p.setProperty("slots", "255");
            Path file = directory.resolve("connection.properties");
            descriptor = file;
            try (var out = Files.newOutputStream(file)) { p.store(out, "Ephemeral test infrastructure"); }
            return file;
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Cannot start shared test infrastructure", e);
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    /** First infrastructure shutdown failure stays primary; later steps attach as suppressed. */
    static RuntimeException combineCloseFailures(RuntimeException first, RuntimeException second) {
        if (second == null) return first;
        if (first == null) return second;
        first.addSuppressed(second);
        return first;
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try { if (redis != null) redis.stop(); } catch (RuntimeException e) { failure = e; }
        try { if (postgres != null) postgres.stop(); } catch (RuntimeException e) { failure = combineCloseFailures(failure, e); }
        if (descriptor != null) {
            try (var paths = Files.walk(descriptor.getParent())) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (Exception e) {
                failure = combineCloseFailures(failure, new IllegalStateException("Cannot remove infrastructure descriptor", e));
            }
        }
        if (failure != null) throw failure;
        System.out.println("PTK infrastructure-closed");
    }
}
