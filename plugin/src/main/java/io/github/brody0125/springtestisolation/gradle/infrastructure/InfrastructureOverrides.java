package io.github.brody0125.springtestisolation.gradle.infrastructure;

import io.github.brody0125.springtestisolation.gradle.IsolatedTestsPlugin;
import org.gradle.api.GradleException;

public final class InfrastructureOverrides {
    private InfrastructureOverrides() {}

    public static void validate(IsolatedTestsPlugin.Options options) {
        String jdbc = options.getJdbcBackend().get();
        String cache = options.getCacheBackend().get();
        if (options.getPostgresImage().isPresent() && !PostgreSqlJdbcInfrastructureProvider.ID.equals(jdbc)) {
            throw new GradleException("postgresImage is only supported when jdbcBackend is 'postgresql'");
        }
        if (options.getRedisImage().isPresent() && !RedisCacheInfrastructureProvider.ID.equals(cache)) {
            throw new GradleException("redisImage is only supported when cacheBackend is 'redis'");
        }
        if (options.getRedisLogicalDatabases().isPresent() && !RedisCacheInfrastructureProvider.ID.equals(cache)) {
            throw new GradleException("redisLogicalDatabases is only supported when cacheBackend is 'redis'");
        }
        if (options.getMaxCacheSlots().isPresent() && !RedisCacheInfrastructureProvider.ID.equals(cache)) {
            throw new GradleException("maxCacheSlots is only supported when cacheBackend is 'redis'");
        }
        try {
            InfrastructureConfiguration.resolve(
                    options.getPostgresImage(),
                    options.getRedisImage(),
                    options.getRedisLogicalDatabases(),
                    options.getMaxCacheSlots());
        } catch (IllegalArgumentException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }
}
