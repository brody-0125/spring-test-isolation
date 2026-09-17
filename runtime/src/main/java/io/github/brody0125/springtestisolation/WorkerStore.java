package io.github.brody0125.springtestisolation;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import redis.clients.jedis.Jedis;

/** One immutable namespace per JVM. Slot files are never reused within a build. */
public final class WorkerStore implements AutoCloseable {
    private static WorkerStore instance;
    private static Throwable poison;
    final Properties descriptor;
    public final String database;
    public final int redisDatabase;
    public final String namespace;
    public final String jdbcUrl;
    private final String redisPassword = UUID.randomUUID().toString();

    public static synchronized void healthy() {
        if (poison != null) throw new IllegalStateException("Worker poisoned: subsequent tests must not execute", poison);
    }
    public static synchronized void poison(Throwable cause) { if (poison == null) poison = cause; }
    /** Registers a shutdown hook only after allocation succeeds; failed construction keeps the slot file until build end. */
    public static synchronized WorkerStore get() {
        healthy();
        if (instance == null) {
            try { instance = new WorkerStore(Path.of(Objects.requireNonNull(
                    System.getProperty("springtestisolation.descriptor"), "Run using the spring-test-isolation Gradle plugin"))); }
            catch (Exception e) { poison(e); throw new IllegalStateException("Cannot allocate worker storage", e); }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { instance.close(); } catch (Exception e) { System.err.println("Worker storage cleanup failed: " + e); }
            }, "spring-test-isolation-cleanup"));
        }
        return instance;
    }
    WorkerStore(Path file) throws Exception {
        descriptor = new Properties();
        try (var in = Files.newInputStream(file)) { descriptor.load(in); }
        int slot = 0;
        for (int i = 1; i <= Integer.parseInt(descriptor.getProperty("slots")); i++) {
            try { Files.createFile(file.getParent().resolve("slot-" + i)); slot = i; break; }
            catch (FileAlreadyExistsException occupied) { /* another process owns this slot */ }
        }
        if (slot == 0) throw new IllegalStateException("No unused Redis slots; stop the build rather than reuse a possibly dirty slot");
        redisDatabase = slot;
        database = "ptk_" + UUID.randomUUID().toString().replace("-", "");
        namespace = database + ":";
        String adminUrl = descriptor.getProperty("jdbc");
        int query = adminUrl.indexOf('?');
        String suffix = query < 0 ? "" : adminUrl.substring(query);
        String base = query < 0 ? adminUrl : adminUrl.substring(0, query);
        jdbcUrl = base.substring(0, base.lastIndexOf('/') + 1) + database + suffix;
        try (Connection c = admin(); Statement s = c.createStatement()) { s.execute("CREATE DATABASE " + quote(database)); }
        System.out.println("PTK database-created " + database);
        try {
            try (Jedis admin = new Jedis(redisHost(), redisPort())) {
                admin.aclSetUser(database, "reset", "on", ">" + redisPassword, "~*", "&" + namespace + "*",
                        "+@all", "-@admin", "-@dangerous", "+flushdb", "+keys", "-swapdb", "-move", "-copy",
                        "-script|flush", "-function|flush", "-function|delete", "-function|load");
            }
            reset();
        }
        catch (Exception e) { try { close(); } catch (Exception cleanup) { e.addSuppressed(cleanup); } throw e; }
        System.out.println("PTK allocated worker=" + System.getProperty("org.gradle.test.worker") + " namespace=" + namespace
                + " run=" + descriptor.getProperty("run") + " task=" + System.getProperty("springtestisolation.task", "standalone")
                + " pid=" + ProcessHandle.current().pid());
    }
    public String username() { return descriptor.getProperty("user"); }
    public String password() { return descriptor.getProperty("password"); }
    public String redisHost() { return descriptor.getProperty("redis.host"); }
    public int redisPort() { return Integer.parseInt(descriptor.getProperty("redis.port")); }
    public String channel(String logicalName) { return namespace + logicalName; }
    /** Redis requires an exact ACL pattern grant for PSUBSCRIBE, even under a broader prefix grant. */
    public String channelPattern(String logicalPattern) {
        String pattern = channel(logicalPattern);
        try (Jedis admin = new Jedis(redisHost(), redisPort())) { admin.aclSetUser(database, "&" + pattern); }
        return pattern;
    }
    public String redisUsername() { return database; }
    public String redisPassword() { return redisPassword; }
    public Jedis redis() {
        Jedis j = new Jedis(redisHost(), redisPort());
        try { j.auth(redisUsername(), redisPassword); j.select(redisDatabase); return j; }
        catch (RuntimeException e) { j.close(); throw e; }
    }
    private Connection admin() throws SQLException { return DriverManager.getConnection(descriptor.getProperty("jdbc"), username(), password()); }
    public Connection connection() throws SQLException { return DriverManager.getConnection(jdbcUrl, username(), password()); }
    static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
    public void reset() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            try (ResultSet rows = s.executeQuery("SELECT schemaname, tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') AND schemaname NOT LIKE 'pg_toast%' AND tablename NOT IN ('flyway_schema_history','databasechangelog','databasechangeloglock')")) {
                while (rows.next()) tables.add(quote(rows.getString(1)) + "." + quote(rows.getString(2)));
            }
            if (!tables.isEmpty()) s.execute("TRUNCATE " + String.join(",", tables) + " RESTART IDENTITY CASCADE");
        }
        try (Jedis redis = redis()) { redis.flushDB(); }
    }
    /** Keeps the first cleanup failure primary so a later Redis error cannot mask DROP DATABASE. */
    static Exception combineCleanupFailures(Exception first, Exception second) {
        if (second == null) return first;
        if (first == null) return second;
        first.addSuppressed(second);
        return first;
    }
    @Override public void close() throws Exception {
        Exception failure = null;
        try (Connection c = admin(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + quote(database) + " WITH (FORCE)");
        } catch (Exception e) { failure = e; }
        try (Jedis admin = new Jedis(redisHost(), redisPort())) {
            admin.select(redisDatabase);
            admin.flushDB();
            admin.aclDelUser(database);
        } catch (Exception e) { failure = combineCleanupFailures(failure, e); }
        if (failure != null) throw failure;
    }
}
