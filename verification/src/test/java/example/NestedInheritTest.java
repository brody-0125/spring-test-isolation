package example;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
class NestedInheritTest {
    @Nested
    class NestedCase {
        @Autowired JdbcTemplate jdbc;
        @Test void inheritedContextRuns() {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM records", Integer.class));
            jdbc.update("INSERT INTO records VALUES (1, 'nested')");
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM records", Integer.class));
        }
    }
}
