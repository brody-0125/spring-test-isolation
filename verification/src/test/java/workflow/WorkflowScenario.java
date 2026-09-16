package workflow;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = WorkflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(listeners = WorkflowScenario.CleanupAudit.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
abstract class WorkflowScenario {
    static final AtomicInteger rounds = new AtomicInteger();
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired WorkerStore store;
    @Autowired TestRestTemplate http;
    @Autowired WorkflowApplication.Flow flow;
    @Autowired ApplicationContext context;

    @Test void linkedResourcesRecoverAndRemainIsolated() throws Exception {
        int round = rounds.incrementAndGet();
        int worker = Integer.parseInt(System.getProperty("org.gradle.test.worker"));
        System.out.println("WORKFLOW start time=" + System.currentTimeMillis() + " worker=" + worker
                + " class=" + getClass().getSimpleName() + " context=" + System.identityHashCode(context));
        for (String table : List.of("orders", "order_items", "outbox", "projection.receipts", "projection.totals"))
            assertEquals(0, count(table), "Previous class leaked into " + table);
        try (var connection = store.redis()) { assertEquals(0, connection.dbSize()); }
        assertTrue(flow.messages.isEmpty(), "Previous class listener state leaked");
        assertEquals(0, flow.processed);

        redis.opsForValue().set("lock:order", "someone-else", Duration.ofSeconds(30));
        assertEquals(409, http.postForEntity("/orders", null, String.class).getStatusCode().value());
        assertEquals("someone-else", redis.opsForValue().get("lock:order"));
        assertEquals(0, count("orders"));
        redis.delete("lock:order");
        assertEquals(1L, http.postForObject("/orders", null, Long.class), "Identity must restart across classes");
        assertFalse(Boolean.TRUE.equals(redis.hasKey("lock:order")), "Lua unlock did not release lock");
        assertEquals(409, http.postForEntity("/orders?rollback=true", null, String.class).getStatusCode().value());
        for (String table : List.of("orders", "order_items", "outbox")) assertEquals(1, count(table));
        assertEquals(0L, redis.opsForList().size("orders:queue"));
        assertTrue(flow.messages.isEmpty());

        assertEquals("INJECTED_AFTER_ENQUEUE", assertThrows(IllegalStateException.class, () -> flow.publish(true)).getMessage());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox WHERE NOT sent", Integer.class));
        flow.publish(false);
        assertEquals(2L, redis.opsForList().size("orders:queue"), "Retry must exercise duplicate delivery");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox WHERE NOT sent", Integer.class));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> flow.consume(true).get(5, TimeUnit.SECONDS));
        assertEquals("INJECTED_AFTER_PROJECTION_COMMIT", failure.getCause().getMessage());
        assertEquals(1, count("projection.receipts"));
        assertNull(redis.opsForValue().get("cache:1"));
        assertTrue(flow.messages.isEmpty());
        flow.consume(false).get(5, TimeUnit.SECONDS);
        assertEquals(1, flow.processed, "Duplicate delivery must not apply the projection twice");
        assertEquals(1, count("projection.receipts"));
        assertEquals(3, jdbc.queryForObject("SELECT quantity FROM projection.totals WHERE order_id=1", Integer.class));
        assertEquals(0L, redis.opsForList().size("orders:queue"));
        assertEquals(store.namespace + "3", http.getForObject("/orders/1", String.class));
        assertEquals(store.namespace + "1", flow.messages.poll(5, TimeUnit.SECONDS));
        assertEquals(store.namespace + "1", flow.messages.poll(5, TimeUnit.SECONDS));

        List<Integer> peers = barrier(round, "ready", worker);
        assertEquals(store.namespace, jdbc.queryForObject("SELECT owner FROM orders WHERE id=1", String.class));
        assertEquals(store.namespace + "3", redis.opsForValue().get("cache:1"));
        // Only one worker erases its own full logical stores while peers keep their live workflow data.
        boolean cleaner = worker == peers.get(0);
        if (cleaner) store.reset();
        barrier(round, "cleaned", worker);
        assertEquals(cleaner ? 0 : 1, count("orders"));
        assertEquals(cleaner ? 0 : 1, count("projection.totals"));
        assertEquals(cleaner ? null : store.namespace + "3", redis.opsForValue().get("cache:1"));
        assertNull(flow.messages.poll(150, TimeUnit.MILLISECONDS), "Foreign namespace Pub/Sub delivery");
        barrier(round, "checked", worker);
        flow.scheduleLate();
        assertFalse(flow.late.isDone(), "Late work must cross the test-body / class-cleanup boundary");
        System.out.println("WORKFLOW verified worker=" + worker + " peers=" + peers.size() + " cleaner=" + cleaner);
        System.out.println("WORKFLOW end time=" + System.currentTimeMillis() + " worker=" + worker + " class=" + getClass().getSimpleName());
    }

    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    /** Reverse callback order: after the runtime's 2980 reset, without reopening a closed context. */
    public static class CleanupAudit extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2970; }
        @Override public void afterTestClass(TestContext test) throws Exception {
            var store = WorkerStore.get();
            try (var connection = store.connection(); var statement = connection.createStatement()) {
                for (String table : List.of("orders", "order_items", "outbox", "projection.receipts", "projection.totals")) {
                    try (var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getInt(1), "Post-boundary residue in " + table);
                    }
                }
            }
            try (var redis = store.redis()) { assertEquals(0, redis.dbSize(), "Post-boundary Redis residue"); }
            System.out.println("WORKFLOW audited worker=" + System.getProperty("org.gradle.test.worker"));
        }
    }

    /** Files are coordination only, under this build's unique descriptor directory. Never application storage. */
    List<Integer> barrier(int round, String phase, int worker) throws Exception {
        int expected = Integer.getInteger("fixture.workflowPeers", 1);
        if (expected == 1) return List.of(worker);
        Path directory = Path.of(System.getProperty("springtestisolation.descriptor")).getParent()
                .resolve("workflow-" + System.getProperty("springtestisolation.task").replace(':', '_') + "-" + round + "-" + phase);
        Files.createDirectories(directory);
        Files.createFile(directory.resolve(Integer.toString(worker)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime() < deadline) {
            try (var files = Files.list(directory)) {
                List<Integer> peers = files.map(p -> Integer.parseInt(p.getFileName().toString())).sorted().toList();
                if (peers.size() == expected) return peers;
                assertTrue(peers.size() < expected, "Unexpected worker count at barrier");
            }
            Thread.sleep(20);
        }
        fail("Timed out waiting for " + expected + " workflow workers at " + phase + " round=" + round);
        return List.of();
    }
}
