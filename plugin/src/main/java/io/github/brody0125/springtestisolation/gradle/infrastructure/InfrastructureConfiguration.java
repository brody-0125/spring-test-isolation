package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.gradle.api.provider.Property;

/** Resolved container settings for one Gradle build (defaults from InfrastructureVersions). */
public final class InfrastructureConfiguration {
    private final String postgresImage;
    private final String mysqlImage;
    private final String oracleImage;
    private final String redisImage;
    private final int redisLogicalDatabases;
    private final int maxCacheSlots;

    public InfrastructureConfiguration(String postgresImage, String mysqlImage, String oracleImage, String redisImage,
            int redisLogicalDatabases, int maxCacheSlots) {
        this.postgresImage = postgresImage;
        this.mysqlImage = mysqlImage;
        this.oracleImage = oracleImage;
        this.redisImage = redisImage;
        this.redisLogicalDatabases = redisLogicalDatabases;
        this.maxCacheSlots = maxCacheSlots;
    }

    public String postgresImage() { return postgresImage; }
    public String mysqlImage() { return mysqlImage; }
    public String oracleImage() { return oracleImage; }
    public String redisImage() { return redisImage; }
    public int redisLogicalDatabases() { return redisLogicalDatabases; }
    public int maxCacheSlots() { return maxCacheSlots; }

    public static InfrastructureConfiguration defaults() {
        return new InfrastructureConfiguration(
                InfrastructureVersions.POSTGRES_IMAGE,
                InfrastructureVersions.MYSQL_IMAGE,
                InfrastructureVersions.ORACLE_IMAGE,
                InfrastructureVersions.REDIS_IMAGE,
                Integer.parseInt(InfrastructureVersions.REDIS_LOGICAL_DATABASES),
                Integer.parseInt(InfrastructureVersions.MAX_WORKER_SLOTS));
    }

    public static InfrastructureConfiguration resolve(
            Property<String> postgresImage,
            Property<String> mysqlImage,
            Property<String> oracleImage,
            Property<String> redisImage,
            Property<Integer> redisLogicalDatabases,
            Property<Integer> maxCacheSlots) {
        InfrastructureConfiguration defaults = defaults();
        String postgres = postgresImage.getOrNull();
        if (postgres == null || postgres.isBlank()) postgres = defaults.postgresImage;
        String mysql = mysqlImage.getOrNull();
        if (mysql == null || mysql.isBlank()) mysql = defaults.mysqlImage;
        String oracle = oracleImage.getOrNull();
        if (oracle == null || oracle.isBlank()) oracle = defaults.oracleImage;
        String redis = redisImage.getOrNull();
        if (redis == null || redis.isBlank()) redis = defaults.redisImage;
        int databases = redisLogicalDatabases.getOrNull() != null
                ? redisLogicalDatabases.get() : defaults.redisLogicalDatabases;
        int slots = maxCacheSlots.getOrNull() != null ? maxCacheSlots.get() : defaults.maxCacheSlots;
        if (slots < 1 || slots > 255) throw new IllegalArgumentException("maxCacheSlots must be between 1 and 255");
        if (databases < 1 || databases > 256) throw new IllegalArgumentException("redisLogicalDatabases must be between 1 and 256");
        if (slots > databases) throw new IllegalArgumentException("maxCacheSlots cannot exceed redisLogicalDatabases");
        return new InfrastructureConfiguration(postgres, mysql, oracle, redis, databases, slots);
    }
}
