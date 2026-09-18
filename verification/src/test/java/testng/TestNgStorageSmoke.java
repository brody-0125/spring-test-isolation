package testng;

import com.github.seregamorph.testsmartcontext.testng.AbstractTestNGSpringIntegrationTest;
import example.TestApplication;
import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@SpringBootTest(classes = TestApplication.class)
public class TestNgStorageSmoke extends AbstractTestNGSpringIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkerStore store;

    @Test public void isolatedWorkerDatabase() {
        System.out.println("EVIDENCE start time=" + System.currentTimeMillis() + " worker="
                + System.getProperty("org.gradle.test.worker") + " class=" + getClass().getSimpleName()
                + " context=" + System.identityHashCode(jdbc.getDataSource()));
        assertNotNull(store.namespace);
        jdbc.update("INSERT INTO records VALUES (1, ?)", store.namespace);
        assertEquals(store.namespace, jdbc.queryForObject("SELECT value FROM records WHERE id=1", String.class));
        System.out.println("EVIDENCE end time=" + System.currentTimeMillis() + " worker="
                + System.getProperty("org.gradle.test.worker") + " class=" + getClass().getSimpleName());
    }
}
