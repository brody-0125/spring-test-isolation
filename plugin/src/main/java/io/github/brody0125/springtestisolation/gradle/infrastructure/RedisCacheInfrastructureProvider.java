package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.testcontainers.containers.GenericContainer;
import java.util.Properties;

public final class RedisCacheInfrastructureProvider implements CacheInfrastructureProvider {
    public static final String ID = "redis";

    @Override public String id() { return ID; }

    @Override public StartedInfrastructure start() {
        GenericContainer<?> redis = new GenericContainer<>(InfrastructureVersions.REDIS_IMAGE).withExposedPorts(6379)
                .withCommand("redis-server", "--databases", InfrastructureVersions.REDIS_LOGICAL_DATABASES);
        redis.start();
        return new StartedInfrastructure(redis, redis.getContainerId(), descriptor -> {
            descriptor.setProperty("redis.host", redis.getHost());
            descriptor.setProperty("redis.port", redis.getMappedPort(6379).toString());
            descriptor.setProperty("slots", InfrastructureVersions.MAX_WORKER_SLOTS);
        });
    }
}
