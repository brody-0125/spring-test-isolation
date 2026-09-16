package example;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class WorkerRecoveryTest {
    @Test void crashDoesNotRecycleStorageIntoRetry() throws Exception {
        String crashed = probe("crash", 17);
        String retry = probe("retry", 0);
        var pattern = Pattern.compile("PROBE (\\w+) (\\d+)");
        var first = pattern.matcher(crashed);
        var second = pattern.matcher(retry);
        assertTrue(first.find(), crashed);
        assertTrue(second.find(), retry);
        assertNotEquals(first.group(1), second.group(1));
        assertNotEquals(first.group(2), second.group(2));
        verifyPartialAllocationRollback();
    }
    private String probe(String mode, int expectedCode) throws Exception {
        return probe(mode, expectedCode, System.getProperty("springtestisolation.descriptor"));
    }
    private void verifyPartialAllocationRollback() throws Exception {
        var properties = new java.util.Properties();
        try (var in = Files.newInputStream(Path.of(System.getProperty("springtestisolation.descriptor")))) { properties.load(in); }
        Path directory = Files.createTempDirectory("ptk-failed-allocation-");
        Path descriptor = directory.resolve("connection.properties");
        properties.setProperty("redis.host", "127.0.0.1");
        properties.setProperty("redis.port", "0");
        try {
            try (var out = Files.newOutputStream(descriptor)) { properties.store(out, "Deliberately unavailable Redis"); }
            String output = probe("allocation-failure", 1, descriptor.toString());
            var created = Pattern.compile("PTK database-created (\\w+)").matcher(output);
            assertTrue(created.find(), "Failure must happen after PostgreSQL allocation: " + output);
            try (var connection = java.sql.DriverManager.getConnection(properties.getProperty("jdbc"), properties.getProperty("user"), properties.getProperty("password"));
                 var statement = connection.prepareStatement("SELECT count(*) FROM pg_database WHERE datname=?")) {
                statement.setString(1, created.group(1));
                try (var result = statement.executeQuery()) { assertTrue(result.next()); assertEquals(0, result.getInt(1), "Partially allocated database leaked"); }
            }
            System.out.println("PARTIAL_ALLOCATION_ROLLBACK_PASS");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private String probe(String mode, int expectedCode, String descriptor) throws Exception {
        Path log = Files.createTempFile("ptk-worker-probe-", ".log");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("fixture.classpath"),
                "-Dspringtestisolation.descriptor=" + descriptor,
                "-Dorg.gradle.test.worker=probe-" + mode, WorkerProbe.class.getName(), mode)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Worker probe timed out");
            String output = Files.readString(log);
            assertEquals(expectedCode, child.exitValue(), output);
            System.out.println(output);
            return output;
        } finally { if (child.isAlive()) child.destroyForcibly(); Files.deleteIfExists(log); }
    }
}
