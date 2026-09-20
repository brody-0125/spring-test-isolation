package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = MavenStorageApp.class)
@ContextConfiguration(initializers = MavenConsumerInfrastructure.PostgresInitializer.class)
class MavenForkIsolationTestB extends MavenConsumerInfrastructure {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test
    void insertsPrimaryKeyOneInWorkerDatabase() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS records (id integer primary key, value text)");
        System.out.println("MAVEN_EVIDENCE class=B fork=" + System.getProperty("surefire.forkNumber", "?")
                + " namespace=" + store.namespace);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM records", Integer.class));
        jdbc.update("INSERT INTO records VALUES (1, ?)", store.namespace);
        assertEquals(store.namespace, jdbc.queryForObject("SELECT value FROM records WHERE id=1", String.class));
    }
}
