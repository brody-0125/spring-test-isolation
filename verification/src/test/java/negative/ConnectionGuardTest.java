package negative;

import io.github.brody0125.springtestisolation.WorkerStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("guard")
@SpringBootTest(classes = {example.TestApplication.class, ConnectionGuardTest.WrongConnection.class})
class ConnectionGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_CONNECTION_BODY"); }
    @TestConfiguration static class WrongConnection {
        @Bean DataSource dataSource(WorkerStore store) {
            return new DriverManagerDataSource(store.jdbcUrl.replace(store.database, "test"), store.username(), store.password());
        }
        @Bean String initializerThatMustNotRun(DataSource dataSource) {
            throw new AssertionError("FORBIDDEN_SQL_INITIALIZER");
        }
    }
}
