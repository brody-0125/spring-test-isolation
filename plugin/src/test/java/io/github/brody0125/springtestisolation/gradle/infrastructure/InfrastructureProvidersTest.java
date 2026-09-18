package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureProvidersTest {
    @Test void defaultBackendsAreRegistered() {
        assertEquals(PostgreSqlJdbcInfrastructureProvider.ID, InfrastructureProviders.jdbc("postgresql").id());
        assertEquals(MySqlJdbcInfrastructureProvider.ID, InfrastructureProviders.jdbc("mysql").id());
        assertEquals(OracleJdbcInfrastructureProvider.ID, InfrastructureProviders.jdbc("oracle").id());
        assertEquals(RedisCacheInfrastructureProvider.ID, InfrastructureProviders.cache("redis").id());
    }
    @Test void unknownBackendThrows() {
        assertThrows(IllegalArgumentException.class, () -> InfrastructureProviders.jdbc("mssql"));
    }
}
