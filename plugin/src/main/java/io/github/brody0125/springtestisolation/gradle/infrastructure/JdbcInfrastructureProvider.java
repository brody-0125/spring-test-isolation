package io.github.brody0125.springtestisolation.gradle.infrastructure;

import java.util.Properties;

/** Starts shared JDBC infrastructure for a Gradle build and publishes descriptor properties. */
public interface JdbcInfrastructureProvider {
    String id();

    StartedInfrastructure start() throws Exception;
}
