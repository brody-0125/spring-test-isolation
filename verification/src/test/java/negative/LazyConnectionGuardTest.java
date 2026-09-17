package negative;

import io.github.brody0125.springtestisolation.WorkerStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("guard")
@SpringBootTest(classes = {example.TestApplication.class, LazyConnectionGuardTest.LazyWrongConnection.class})
class LazyConnectionGuardTest {
    @Test void forbiddenBody() { throw new AssertionError("FORBIDDEN_LAZY_CONNECTION_BODY"); }
    @TestConfiguration static class LazyWrongConnection {
        @Bean @Lazy DataSource lazyBypass(WorkerStore store) {
            return new DriverManagerDataSource(store.jdbcUrl.replace(store.database, "test"), store.username(), store.password());
        }
    }
}
