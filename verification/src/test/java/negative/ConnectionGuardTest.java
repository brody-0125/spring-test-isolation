package negative;

import io.github.brody0125.springtestisolation.WorkerStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("guard")
@SpringBootTest(classes = {ConnectionGuardTest.App.class, ConnectionGuardTest.WrongConnection.class})
class ConnectionGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_CONNECTION_BODY"); }
    @SpringBootApplication static class App {}
    @TestConfiguration static class WrongConnection {
        @Bean DataSource dataSource(WorkerStore store) {
            if (!store.jdbcUrl.contains(store.database)) {
                return new DriverManagerDataSource(
                        store.jdbcUrl,
                        store.infrastructureProperty("user"),
                        store.infrastructureProperty("password"));
            }
            return new DriverManagerDataSource(store.jdbcUrl.replace(store.database, "test"), store.username(), store.password());
        }
        @Bean String initializerThatMustNotRun(DataSource dataSource) {
            throw new AssertionError("FORBIDDEN_SQL_INITIALIZER");
        }
    }
}

