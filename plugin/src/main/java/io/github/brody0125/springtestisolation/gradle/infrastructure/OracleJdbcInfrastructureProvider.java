package io.github.brody0125.springtestisolation.gradle.infrastructure;

import org.testcontainers.containers.OracleContainer;

public final class OracleJdbcInfrastructureProvider implements JdbcInfrastructureProvider {
    public static final String ID = "oracle";

    @Override public String id() { return ID; }

    @Override public StartedInfrastructure start(InfrastructureConfiguration configuration) {
        OracleContainer oracle = new OracleContainer(configuration.oracleImage());
        oracle.start();
        return new StartedInfrastructure(oracle, oracle.getContainerId(), descriptor -> {
            descriptor.setProperty("jdbc", oracle.getJdbcUrl());
            // SYSTEM can CREATE/DROP worker users in the image PDB; the app user cannot.
            descriptor.setProperty("user", "system");
            descriptor.setProperty("password", oracle.getPassword());
        });
    }
}
