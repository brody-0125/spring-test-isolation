package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.testcontainers.containers.GenericContainer;
import java.util.Properties;

public final class RedisCacheInfrastructureProvider implements CacheInfrastructureProvider {
    public static final String ID = "redis";

    @Override public String id() { return ID; }

    @Override public StartedInfrastructure start(InfrastructureConfiguration configuration) {
        GenericContainer<?> redis = new GenericContainer<>(configuration.redisImage()).withExposedPorts(6379)
                .withCommand("redis-server", "--databases", Integer.toString(configuration.redisLogicalDatabases()),
                        "--notify-keyspace-events", "Egx");
        redis.start();
        return new StartedInfrastructure(redis, redis.getContainerId(), descriptor -> {
            descriptor.setProperty("redis.host", redis.getHost());
            descriptor.setProperty("redis.port", redis.getMappedPort(6379).toString());
            descriptor.setProperty("slots", Integer.toString(configuration.maxCacheSlots()));
        });
    }
}
