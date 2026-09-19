package io.github.brody0125.springtestisolation;

import io.github.brody0125.springtestisolation.cache.CacheWorkerBackend;
import io.github.brody0125.springtestisolation.cache.CacheWorkerBackends;
import io.github.brody0125.springtestisolation.jdbc.JdbcWorkerBackend;
import io.github.brody0125.springtestisolation.jdbc.JdbcWorkerBackends;
import io.github.brody0125.springtestisolation.jdbc.PostgreSqlJdbcWorkerBackend;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import redis.clients.jedis.Jedis;

/** One immutable namespace per JVM. Slot files are never reused within a build. */
public final class WorkerStore implements AutoCloseable {
    private static WorkerStore instance;
    private static Throwable poison;
    final Properties descriptor;
    final JdbcWorkerBackend jdbcBackend;
    final CacheWorkerBackend cacheBackend;
    public final String database;
    public final int redisDatabase;
    public final String namespace;
    public final String jdbcUrl;
    private String workerUsername;
    private String workerPassword;
    private final String redisPassword = UUID.randomUUID().toString();

    public static synchronized void healthy() {
        if (poison != null) throw new IllegalStateException("Worker poisoned: subsequent tests must not execute", poison);
    }
    public static synchronized void poison(Throwable cause) { if (poison == null) poison = cause; }
    /** Registers a shutdown hook only after allocation succeeds; failed construction keeps the slot file until build end. */
    public static synchronized WorkerStore get() {
        return get(new Properties());
    }
    public static synchronized WorkerStore get(Properties overlay) {
        healthy();
        if (instance == null) {
            try { instance = new WorkerStore(Path.of(Objects.requireNonNull(
                    System.getProperty("springtestisolation.descriptor"), "Run using the spring-test-isolation Gradle plugin")), overlay); }
            catch (Exception e) { poison(e); throw new IllegalStateException("Cannot allocate worker storage", e); }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { instance.close(); } catch (Exception e) { System.err.println("Worker storage cleanup failed: " + e); }
            }, "spring-test-isolation-cleanup"));
        }
        return instance;
    }
    WorkerStore(Path file, Properties overlay) throws Exception {
        descriptor = new Properties();
        try (var in = Files.newInputStream(file)) { descriptor.load(in); }
        copyIfBlank(descriptor, InfrastructureDescriptor.JDBC_URL, System.getProperty("spring.datasource.url"));
        copyIfBlank(descriptor, InfrastructureDescriptor.JDBC_USER, System.getProperty("spring.datasource.username"));
        copyIfBlank(descriptor, InfrastructureDescriptor.JDBC_PASSWORD, System.getProperty("spring.datasource.password"));
        copyIfBlank(descriptor, InfrastructureDescriptor.REDIS_HOST, System.getProperty("spring.data.redis.host"));
        copyIfBlank(descriptor, InfrastructureDescriptor.REDIS_PORT, System.getProperty("spring.data.redis.port"));
        if (overlay != null) {
            for (String name : overlay.stringPropertyNames()) {
                descriptor.setProperty(name, overlay.getProperty(name));
            }
        }
        if (blank(descriptor.getProperty(InfrastructureDescriptor.JDBC_URL))) {
            throw new IllegalStateException(
                    "spring.datasource.url is required; spring-test-isolation attaches to consumer JDBC and does not start a database");
        }
        jdbcBackend = JdbcWorkerBackends.resolve(descriptor);
        cacheBackend = CacheWorkerBackends.resolve(descriptor);
        int slot = 0;
        for (int i = 1; i <= Integer.parseInt(descriptor.getProperty(InfrastructureDescriptor.CACHE_SLOTS)); i++) {
            try { Files.createFile(file.getParent().resolve("slot-" + i)); slot = i; break; }
            catch (FileAlreadyExistsException occupied) { /* another process owns this slot */ }
        }
        if (slot == 0) throw new IllegalStateException("No unused cache slots; stop the build rather than reuse a possibly dirty slot");
        redisDatabase = slot;
        database = "ptk_" + UUID.randomUUID().toString().replace("-", "");
        namespace = database + ":";
        jdbcUrl = jdbcBackend.buildWorkerJdbcUrl(this, database);
        jdbcBackend.createWorkerDatabase(this, database);
        System.out.println("PTK database-created " + database);
        try {
            cacheBackend.provisionWorker(this, redisDatabase, database, redisPassword);
            reset();
        }
        catch (Exception e) { try { close(); } catch (Exception cleanup) { e.addSuppressed(cleanup); } throw e; }
        System.out.println("PTK allocated worker=" + System.getProperty("org.gradle.test.worker") + " namespace=" + namespace
                + " run=" + descriptor.getProperty(InfrastructureDescriptor.RUN_ID) + " task=" + System.getProperty("springtestisolation.task", "standalone")
                + " pid=" + ProcessHandle.current().pid());
    }
    public String username() {
        return workerUsername != null ? workerUsername : descriptor.getProperty(InfrastructureDescriptor.JDBC_USER);
    }
    public String password() {
        return workerPassword != null ? workerPassword : descriptor.getProperty(InfrastructureDescriptor.JDBC_PASSWORD);
    }
    public void useWorkerJdbcCredentials(String username, String password) {
        this.workerUsername = username;
        this.workerPassword = password;
    }
    public String redisHost() { return descriptor.getProperty(InfrastructureDescriptor.REDIS_HOST); }
    public int redisPort() { return Integer.parseInt(descriptor.getProperty(InfrastructureDescriptor.REDIS_PORT)); }
    public String channel(String logicalName) { return namespace + logicalName; }
    public String redisKeyeventChannel(String event) { return "__keyevent@" + redisDatabase + "__:" + event; }
    /** Redis requires an exact ACL pattern grant for PSUBSCRIBE, even under a broader prefix grant. */
    public String channelPattern(String logicalPattern) {
        String pattern = channel(logicalPattern);
        cacheBackend.grantChannelPattern(this, pattern);
        return pattern;
    }
    public String redisUsername() { return database; }
    public String redisPassword() { return redisPassword; }
    public Jedis redis() { return openWorkerRedis(); }
    public String infrastructureProperty(String key) { return descriptor.getProperty(key); }
    public Jedis openAdminRedis() { return new Jedis(redisHost(), redisPort()); }
    public Jedis openWorkerRedis() {
        Jedis j = new Jedis(redisHost(), redisPort());
        try { j.auth(redisUsername(), redisPassword); j.select(redisDatabase); return j; }
        catch (RuntimeException e) { j.close(); throw e; }
    }
    public Connection openAdminJdbc() throws SQLException {
        return DriverManager.getConnection(
                descriptor.getProperty(InfrastructureDescriptor.JDBC_URL),
                descriptor.getProperty(InfrastructureDescriptor.JDBC_USER),
                descriptor.getProperty(InfrastructureDescriptor.JDBC_PASSWORD));
    }
    public Connection connection() throws SQLException { return openWorkerJdbc(); }
    public Connection openWorkerJdbc() throws SQLException { return DriverManager.getConnection(jdbcUrl, username(), password()); }
    static String quote(String identifier) { return PostgreSqlJdbcWorkerBackend.quoteIdentifier(identifier); }
    public void reset() throws Exception {
        jdbcBackend.resetWorkerData(this);
        cacheBackend.resetWorkerData(this);
    }
    /** Keeps the first cleanup failure primary so a later cache error cannot mask DROP DATABASE. */
    static Exception combineCleanupFailures(Exception first, Exception second) {
        if (second == null) return first;
        if (first == null) return second;
        first.addSuppressed(second);
        return first;
    }
    @Override public void close() throws Exception {
        Exception failure = null;
        try { jdbcBackend.dropWorkerDatabase(this, database); } catch (Exception e) { failure = e; }
        try { cacheBackend.teardownWorker(this, redisDatabase, database); }
        catch (Exception e) { failure = combineCleanupFailures(failure, e); }
        if (failure != null) throw failure;
    }
    private static void copyIfBlank(Properties target, String key, String value) {
        if (!blank(target.getProperty(key)) || blank(value)) return;
        target.setProperty(key, value);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
