package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "jdbc.backend", matches = "oracle")
@SpringBootTest(classes = OracleTestApplication.class)
class OracleFlywaySmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test void flywayMigratesWorkerDatabase() {
        var result = Flyway.configure().dataSource(store.jdbcUrl, store.username(), store.password())
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0").load().migrate();
        assertEquals(1, result.migrationsExecuted, () -> "expected V1 migration on a non-empty worker schema");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_smoke", Integer.class));
    }
}
