package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
abstract class JdbcTcIsolationScenario {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test void initFunctionRanOnWorkerDatabaseAndHidesRowsFromOtherWorkers() {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tc_init_marker", Integer.class));
        jdbc.update("INSERT INTO tc_init_marker VALUES (1, ?)", store.namespace);
        assertEquals(store.namespace, jdbc.queryForObject("SELECT worker FROM tc_init_marker WHERE id = 1", String.class));
        System.out.println("JDBC_TC_INIT worker=" + System.getProperty("org.gradle.test.worker") + " db=" + store.database);
    }
}
