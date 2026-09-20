package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
abstract class MavenConsumerInfrastructure {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine");

    static class PostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword()
            ).applyTo(context);
        }
    }

    protected void assertPrimaryKeyOneIsolated(String label, JdbcTemplate jdbc, WorkerStore store) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS records (id integer primary key, value text)");
        System.out.println("MAVEN_EVIDENCE class=" + label + " fork=" + System.getProperty("surefire.forkNumber", "?")
                + " namespace=" + store.namespace);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM records", Integer.class));
        jdbc.update("INSERT INTO records VALUES (1, ?)", store.namespace);
        assertEquals(store.namespace, jdbc.queryForObject("SELECT value FROM records WHERE id=1", String.class));
    }
}
