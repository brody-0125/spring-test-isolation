package session;

import io.github.brody0125.springtestisolation.WorkerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SessionApplication.class)
@TestExecutionListeners(listeners = SessionScenario.CleanupAudit.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
abstract class SessionScenario {
    static final AtomicInteger rounds = new AtomicInteger();
    @Autowired StringRedisTemplate redis;
    @Autowired WorkerStore store;
    @Autowired SessionApplication.SessionIndexer indexer;
    @Autowired ApplicationContext context;

    @Test void indexedSessionKeyeventsStayIsolated() throws Exception {
        int round = rounds.incrementAndGet();
        int worker = Integer.parseInt(System.getProperty("org.gradle.test.worker"));
        System.out.println("SESSION start worker=" + worker + " class=" + getClass().getSimpleName()
                + " context=" + System.identityHashCode(context));
        assertTrue(indexer.keyEvents.isEmpty(), "Previous class listener state leaked");
        try (var connection = store.redis()) { assertEquals(0, connection.dbSize()); }

        String principal = getClass().getSimpleName();
        String sessionId = indexer.createSession(principal);
        assertEquals(sessionId, redis.opsForValue().get(store.channel("index:PRINCIPAL:" + principal)));
        System.out.println("SESSION indexed worker=" + worker + " namespace=" + store.namespace + " session=" + sessionId);

        indexer.deleteSession(sessionId, principal);
        assertTrue(awaitKeyevent("del", store.channel("sessions:") + sessionId), "Missing del keyevent");

        String ttlSession = indexer.createSession(principal + "-ttl");
        indexer.expireSession(ttlSession);
        assertTrue(awaitKeyevent("expired", store.channel("sessions:expires:") + ttlSession),
                "Missing expired keyevent");

        List<Integer> peers = barrier(round, "ready", worker);
        Path exchange = barrierDirectory(round, "exchange");
        Files.createDirectories(exchange);
        Files.writeString(exchange.resolve(Integer.toString(worker)), store.namespace + "|" + sessionId);
        barrier(round, "exchanged", worker);
        Set<String> namespaces = new HashSet<>();
        try (var files = Files.list(exchange)) {
            for (Path file : files.toList()) {
                String[] parts = Files.readString(file).split("\\|", 2);
                assertEquals(2, parts.length, "Malformed exchange line");
                String namespace = parts[0];
                assertTrue(namespace.startsWith("ptk_"), "Unexpected namespace: " + namespace);
                assertTrue(namespaces.add(namespace), "SESSION LEAK duplicate namespace index owner: " + namespace);
                if (!namespace.equals(store.namespace)) {
                    assertNotEquals(sessionId, parts[1], "SESSION LEAK foreign session id visible");
                }
            }
        }
        assertEquals(peers.size(), namespaces.size(), "SESSION LEAK missing peer namespace exchange");

        System.out.println("SESSION verified worker=" + worker + " peers=" + peers.size());
        System.out.println("SESSION end worker=" + worker + " class=" + getClass().getSimpleName());
    }

    boolean awaitKeyevent(String kind, String keyBody) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            for (String event : indexer.keyEvents) {
                if (event.contains("__:" + kind) && event.contains(keyBody)) return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    public static class CleanupAudit extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2970; }
        @Override public void afterTestClass(TestContext test) {
            var store = WorkerStore.get();
            var indexer = test.getApplicationContext().getBean(SessionApplication.SessionIndexer.class);
            assertTrue(indexer.keyEvents.isEmpty(), "Post-boundary listener state leaked");
            try (var redis = store.redis()) {
                for (String key : redis.keys(store.namespace + "*")) {
                    assertTrue(key.startsWith(store.namespace), "Post-boundary foreign key: " + key);
                }
                assertEquals(0, redis.dbSize(), "Post-boundary session Redis residue");
            }
            System.out.println("SESSION audited worker=" + System.getProperty("org.gradle.test.worker"));
        }
    }

    List<Integer> barrier(int round, String phase, int worker) throws Exception {
        int expected = Integer.getInteger("fixture.sessionPeers", 1);
        if (expected == 1) return List.of(worker);
        Path directory = barrierDirectory(round, phase);
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
        fail("Timed out waiting for " + expected + " session workers at " + phase + " round=" + round);
        return List.of();
    }

    Path barrierDirectory(int round, String phase) {
        return Path.of(System.getProperty("springtestisolation.descriptor")).getParent()
                .resolve("session-" + System.getProperty("springtestisolation.task").replace(':', '_') + "-" + round + "-" + phase);
    }
}
