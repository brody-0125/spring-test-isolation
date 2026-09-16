package example;

import io.github.brody0125.springtestisolation.ClassBoundary;
import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.*;

@SpringBootApplication
public class TestApplication {
    @Bean Work work(JdbcTemplate jdbc) { return new Work(jdbc); }
    static class Work implements ClassBoundary, AutoCloseable {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final JdbcTemplate jdbc;
        volatile boolean accepting = true;
        volatile int localCount;
        Work(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            jdbc.execute("CREATE TABLE IF NOT EXISTS records (id integer primary key, value text)");
        }
        synchronized Future<?> submit(Runnable work) {
            if (!accepting) throw new IllegalStateException("Class is quiescing");
            return executor.submit(work);
        }
        public void quiesce() throws Exception {
            synchronized (this) { accepting = false; }
            executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
        }
        public void reset() { localCount = 0; }
        public void resume() { accepting = true; }
        public void close() throws Exception {
            quiesce(); executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("Executor leaked");
            // Deliberately write from destruction: the late reset must remove this row.
            jdbc.update("INSERT INTO records VALUES (999, 'destroy') ON CONFLICT DO NOTHING");
        }
    }
    @RestController static class Endpoint {
        private final JdbcTemplate jdbc;
        private final WorkerStore store;
        Endpoint(JdbcTemplate jdbc, WorkerStore store) { this.jdbc = jdbc; this.store = store; }
        @PostMapping("/write") String write() {
            jdbc.update("INSERT INTO records VALUES (1, ?)", store.namespace);
            return store.namespace;
        }
    }
}
