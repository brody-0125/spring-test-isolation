package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = MavenStorageApp.class)
@ContextConfiguration(initializers = MavenConsumerInfrastructure.PostgresInitializer.class)
class MavenForkIsolationTestB extends MavenConsumerInfrastructure {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test
    void insertsPrimaryKeyOneInWorkerDatabase() {
        assertPrimaryKeyOneIsolated("B", jdbc, store);
    }
}
