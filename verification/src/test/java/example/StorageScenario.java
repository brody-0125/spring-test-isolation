package example;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.PatternTopic;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class StorageScenario {
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired WorkerStore store;
    @Autowired TestApplication.Work work;
    @Autowired TestRestTemplate http;
    @Autowired ApplicationContext context;

    @Test void isolatedStorageAndHttpAndDelayedWork() throws Exception {
        System.out.println("EVIDENCE start time=" + System.currentTimeMillis() + " worker=" + System.getProperty("org.gradle.test.worker")
                + " class=" + getClass().getSimpleName() + " context=" + System.identityHashCode(context));
        var memory = java.lang.management.ManagementFactory.getMemoryMXBean();
        System.out.println("METRICS worker=" + System.getProperty("org.gradle.test.worker")
                + " heapUsed=" + memory.getHeapMemoryUsage().getUsed()
                + " heapCommitted=" + memory.getHeapMemoryUsage().getCommitted()
                + " nonHeapUsed=" + memory.getNonHeapMemoryUsage().getUsed()
                + " threads=" + java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount()
                + " connections=" + jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM records", Integer.class));
        assertNull(redis.opsForValue().get("same-key"));
        assertEquals(0, work.localCount);
        work.localCount++;
        assertEquals(store.namespace, http.postForObject("/write", null, String.class));
        redis.opsForValue().set("same-key", store.namespace);
        var rejected = assertThrows(org.springframework.dao.DataAccessException.class, () ->
                redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                    connection.serverCommands().flushAll(); return null;
                }));
        assertTrue(rejected.getMostSpecificCause().getMessage().contains("NOPERM"));
        var received = new LinkedBlockingQueue<String>();
        var listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(redis.getConnectionFactory());
        listener.addMessageListener((message, pattern) -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new PatternTopic(store.channelPattern("events:*")));
        listener.afterPropertiesSet();
        listener.start();
        try {
            redis.convertAndSend(store.channel("events:created"), store.namespace);
            assertEquals(store.namespace, received.poll(5, TimeUnit.SECONDS));
            assertNull(received.poll(200, TimeUnit.MILLISECONDS));
        } finally { listener.stop(); listener.destroy(); }
        Thread.sleep(1000);
        assertEquals(store.namespace, redis.opsForValue().get("same-key"));
        assertEquals(store.namespace, jdbc.queryForObject("SELECT value FROM records WHERE id=1", String.class));
        work.submit(() -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            jdbc.update("INSERT INTO records VALUES (2, 'late')");
        });
        System.out.println("EVIDENCE end time=" + System.currentTimeMillis() + " worker=" + System.getProperty("org.gradle.test.worker")
                + " class=" + getClass().getSimpleName());
    }
}
