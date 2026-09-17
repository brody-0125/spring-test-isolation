package io.github.brody0125.springtestisolation;

/** Keys in the build-owned connection descriptor (see VERIFICATION.md). */
public final class InfrastructureDescriptor {
    public static final String JDBC_BACKEND = "jdbc.backend";
    public static final String CACHE_BACKEND = "cache.backend";
    public static final String JDBC_URL = "jdbc";
    public static final String JDBC_USER = "user";
    public static final String JDBC_PASSWORD = "password";
    public static final String REDIS_HOST = "redis.host";
    public static final String REDIS_PORT = "redis.port";
    public static final String CACHE_SLOTS = "slots";
    public static final String RUN_ID = "run";

    public static final String DEFAULT_JDBC_BACKEND = "postgresql";
    public static final String DEFAULT_CACHE_BACKEND = "redis";

    private InfrastructureDescriptor() {}
}
