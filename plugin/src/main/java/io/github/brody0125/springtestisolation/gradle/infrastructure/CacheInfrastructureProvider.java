package io.github.brody0125.springtestisolation.gradle.infrastructure;

/** Starts shared cache infrastructure for a Gradle build and publishes descriptor properties. */
public interface CacheInfrastructureProvider {
    String id();

    StartedInfrastructure start(InfrastructureConfiguration configuration) throws Exception;
}
