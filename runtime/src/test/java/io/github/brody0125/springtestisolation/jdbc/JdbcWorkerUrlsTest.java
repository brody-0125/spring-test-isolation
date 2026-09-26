package io.github.brody0125.springtestisolation.jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWorkerUrlsTest {
    @Test void postgresUrlSwapsDatabaseSegment() {
        String admin = "jdbc:postgresql://localhost:5432/admin_db?sslmode=disable";
        assertEquals("jdbc:postgresql://localhost:5432/ptk_worker?sslmode=disable",
                JdbcWorkerUrls.withDatabaseName(admin, "ptk_worker"));
    }

    @Test void testcontainersUrlRequiresDaemonMode() {
        String url = "jdbc:tc:postgresql:16-alpine:///admin?TC_INITFUNCTION=com.example.Init::run";
        var error = assertThrows(IllegalStateException.class, () -> JdbcWorkerUrls.validateAdminUrl(url));
        assertTrue(error.getMessage().contains("TC_DAEMON"));
    }

    @Test void testcontainersUrlSwapsDatabaseAndKeepsInitFunction() {
        String admin = "jdbc:tc:postgresql:16-alpine:///admin?TC_DAEMON=true&TC_INITFUNCTION=com.example.Init::run";
        JdbcWorkerUrls.validateAdminUrl(admin);
        String worker = JdbcWorkerUrls.withDatabaseName(admin, "ptk_worker");
        assertTrue(worker.contains("ptk_worker"));
        assertTrue(worker.contains("TC_INITFUNCTION=com.example.Init::run"));
        assertFalse(worker.contains("/admin"));
    }

    @Test void detectsInitFunction() {
        assertTrue(JdbcWorkerUrls.hasInitFunction(
                "jdbc:tc:mysql:8.4.5:///db?TC_DAEMON=true&TC_INITFUNCTION=com.example.Init::run"));
        assertFalse(JdbcWorkerUrls.hasInitFunction("jdbc:postgresql://localhost/db"));
    }
}
