package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "jdbc.backend", matches = "oracle")
@SpringBootTest(classes = OracleTestApplication.class)
class OracleStorageSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test void resetClearsWorkerTables() throws Exception {
        jdbc.execute("""
                BEGIN
                  EXECUTE IMMEDIATE 'CREATE TABLE oracle_smoke (id NUMBER PRIMARY KEY)';
                EXCEPTION
                  WHEN OTHERS THEN
                    IF SQLCODE != -955 THEN RAISE; END IF;
                END;
                """);
        jdbc.update("INSERT INTO oracle_smoke VALUES (1)");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oracle_smoke", Integer.class));
        store.reset();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oracle_smoke", Integer.class));
    }
}
