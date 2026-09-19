package io.github.brody0125.springtestisolation;

import io.github.brody0125.springtestisolation.cache.CacheWorkerBackends;
import io.github.brody0125.springtestisolation.jdbc.JdbcWorkerBackends;
import io.github.brody0125.springtestisolation.jdbc.PostgreSqlJdbcWorkerBackend;
import io.github.brody0125.springtestisolation.cache.RedisCacheWorkerBackend;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackendRegistryTest {
    @Test void defaultsResolvePostgresqlAndRedis() {
        var descriptor = new Properties();
        assertEquals(PostgreSqlJdbcWorkerBackend.ID, JdbcWorkerBackends.resolve(descriptor).id());
        assertEquals(RedisCacheWorkerBackend.ID, CacheWorkerBackends.resolve(descriptor).id());
    }
    @Test void mysqlJdbcBackendResolves() {
        var descriptor = new Properties();
        descriptor.setProperty(InfrastructureDescriptor.JDBC_BACKEND, "mysql");
        assertEquals("mysql", JdbcWorkerBackends.resolve(descriptor).id());
    }

    @Test void oracleJdbcBackendResolves() {
        var descriptor = new Properties();
        descriptor.setProperty(InfrastructureDescriptor.JDBC_BACKEND, "oracle");
        assertEquals("oracle", JdbcWorkerBackends.resolve(descriptor).id());
    }

    @Test void unknownJdbcBackendFailsFast() {
        var descriptor = new Properties();
        descriptor.setProperty(InfrastructureDescriptor.JDBC_BACKEND, "mssql");
        assertThrows(IllegalStateException.class, () -> JdbcWorkerBackends.resolve(descriptor));
    }
}
