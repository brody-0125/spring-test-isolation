package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "jdbc.backend", matches = "mysql")
@SpringBootTest(classes = TestApplication.class)
class MysqlStorageSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test void resetClearsWorkerTables() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mysql_smoke (id INT PRIMARY KEY)");
        jdbc.update("INSERT INTO mysql_smoke VALUES (1)");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mysql_smoke", Integer.class));
        store.reset();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mysql_smoke", Integer.class));
    }
}
