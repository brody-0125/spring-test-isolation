package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.gradle.api.provider.Property;

/** Resolved container settings for one Gradle build (defaults from InfrastructureVersions). */
public final class InfrastructureConfiguration {
    private final String postgresImage;
    private final String redisImage;
    private final int redisLogicalDatabases;
    private final int maxCacheSlots;

    public InfrastructureConfiguration(String postgresImage, String redisImage, int redisLogicalDatabases, int maxCacheSlots) {
        this.postgresImage = postgresImage;
        this.redisImage = redisImage;
        this.redisLogicalDatabases = redisLogicalDatabases;
        this.maxCacheSlots = maxCacheSlots;
    }

    public String postgresImage() { return postgresImage; }
    public String redisImage() { return redisImage; }
    public int redisLogicalDatabases() { return redisLogicalDatabases; }
    public int maxCacheSlots() { return maxCacheSlots; }

    public static InfrastructureConfiguration defaults() {
        return new InfrastructureConfiguration(
                InfrastructureVersions.POSTGRES_IMAGE,
                InfrastructureVersions.REDIS_IMAGE,
                Integer.parseInt(InfrastructureVersions.REDIS_LOGICAL_DATABASES),
                Integer.parseInt(InfrastructureVersions.MAX_WORKER_SLOTS));
    }

    public static InfrastructureConfiguration resolve(
            Property<String> postgresImage,
            Property<String> redisImage,
            Property<Integer> redisLogicalDatabases,
            Property<Integer> maxCacheSlots) {
        InfrastructureConfiguration defaults = defaults();
        String postgres = postgresImage.getOrNull();
        if (postgres == null || postgres.isBlank()) postgres = defaults.postgresImage;
        String redis = redisImage.getOrNull();
        if (redis == null || redis.isBlank()) redis = defaults.redisImage;
        int databases = redisLogicalDatabases.getOrNull() != null
                ? redisLogicalDatabases.get() : defaults.redisLogicalDatabases;
        int slots = maxCacheSlots.getOrNull() != null ? maxCacheSlots.get() : defaults.maxCacheSlots;
        if (slots < 1 || slots > 255) throw new IllegalArgumentException("maxCacheSlots must be between 1 and 255");
        if (databases < 1 || databases > 256) throw new IllegalArgumentException("redisLogicalDatabases must be between 1 and 256");
        if (slots > databases) throw new IllegalArgumentException("maxCacheSlots cannot exceed redisLogicalDatabases");
        return new InfrastructureConfiguration(postgres, redis, databases, slots);
    }
}
