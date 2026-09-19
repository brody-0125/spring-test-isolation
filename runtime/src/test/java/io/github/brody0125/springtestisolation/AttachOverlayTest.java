package io.github.brody0125.springtestisolation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AttachOverlayTest {
    @Test void blankJdbcUrlFailsBeforeAllocation() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "  ");
        var error = assertThrows(IllegalStateException.class, () -> IsolationCustomizerFactory.attachOverlay(env));
        assertTrue(error.getMessage().contains("spring.datasource.url"));
    }

    @Test void missingJdbcUrlFailsBeforeAllocation() {
        assertThrows(IllegalStateException.class, () -> IsolationCustomizerFactory.attachOverlay(new MockEnvironment()));
    }

    @Test void leftoverJedisHostDoesNotEnableRedis() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost/db")
                .withProperty("redis.connection.host", "localhost");
        var overlay = IsolationCustomizerFactory.attachOverlay(env);
        assertEquals(InfrastructureDescriptor.NONE_CACHE_BACKEND, overlay.getProperty(InfrastructureDescriptor.CACHE_BACKEND));
        assertNull(overlay.getProperty(InfrastructureDescriptor.REDIS_HOST));
    }

    @Test void springDataRedisHostEnablesRedis() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost/db")
                .withProperty("spring.datasource.username", "test")
                .withProperty("spring.data.redis.host", "127.0.0.1")
                .withProperty("spring.data.redis.port", "6380");
        var overlay = IsolationCustomizerFactory.attachOverlay(env);
        assertEquals("redis", overlay.getProperty(InfrastructureDescriptor.CACHE_BACKEND));
        assertEquals("127.0.0.1", overlay.getProperty(InfrastructureDescriptor.REDIS_HOST));
        assertEquals("6380", overlay.getProperty(InfrastructureDescriptor.REDIS_PORT));
        assertEquals("jdbc:postgresql://localhost/db", overlay.getProperty(InfrastructureDescriptor.JDBC_URL));
        assertEquals("test", overlay.getProperty(InfrastructureDescriptor.JDBC_USER));
    }
}
