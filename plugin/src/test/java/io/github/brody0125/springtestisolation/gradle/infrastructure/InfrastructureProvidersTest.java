package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureProvidersTest {
    @Test void defaultBackendsAreRegistered() {
        assertEquals(PostgreSqlJdbcInfrastructureProvider.ID, InfrastructureProviders.jdbc("postgresql").id());
        assertEquals(RedisCacheInfrastructureProvider.ID, InfrastructureProviders.cache("redis").id());
    }
    @Test void unknownBackendThrows() {
        assertThrows(IllegalArgumentException.class, () -> InfrastructureProviders.jdbc("mysql"));
    }
}
