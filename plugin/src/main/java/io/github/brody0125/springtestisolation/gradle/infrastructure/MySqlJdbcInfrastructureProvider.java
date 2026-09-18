package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.testcontainers.mysql.MySQLContainer;

public final class MySqlJdbcInfrastructureProvider implements JdbcInfrastructureProvider {
    public static final String ID = "mysql";

    @Override public String id() { return ID; }

    @Override public StartedInfrastructure start(InfrastructureConfiguration configuration) {
        MySQLContainer mysql = new MySQLContainer(configuration.mysqlImage());
        mysql.start();
        return new StartedInfrastructure(mysql, mysql.getContainerId(), descriptor -> {
            descriptor.setProperty("jdbc", mysql.getJdbcUrl());
            // Root can create per-worker databases and grant the fixture user access.
            descriptor.setProperty("user", "root");
            descriptor.setProperty("password", mysql.getPassword());
        });
    }
}
