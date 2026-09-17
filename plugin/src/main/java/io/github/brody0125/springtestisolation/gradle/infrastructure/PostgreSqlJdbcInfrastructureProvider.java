package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.Properties;

public final class PostgreSqlJdbcInfrastructureProvider implements JdbcInfrastructureProvider {
    public static final String ID = "postgresql";

    @Override public String id() { return ID; }

    @Override public StartedInfrastructure start() {
        PostgreSQLContainer postgres = new PostgreSQLContainer(InfrastructureVersions.POSTGRES_IMAGE);
        postgres.start();
        return new StartedInfrastructure(postgres, postgres.getContainerId(), descriptor -> {
            descriptor.setProperty("jdbc", postgres.getJdbcUrl());
            descriptor.setProperty("user", postgres.getUsername());
            descriptor.setProperty("password", postgres.getPassword());
        });
    }
}
