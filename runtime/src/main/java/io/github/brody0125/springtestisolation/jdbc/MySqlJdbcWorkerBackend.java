package io.github.brody0125.springtestisolation.jdbc;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class MySqlJdbcWorkerBackend implements JdbcWorkerBackend {
    public static final String ID = "mysql";

    @Override public String id() { return ID; }

    @Override public String buildWorkerJdbcUrl(WorkerStore store, String databaseName) {
        return JdbcWorkerUrls.withDatabaseName(store.infrastructureProperty(InfrastructureDescriptor.JDBC_URL), databaseName);
    }

    @Override public void createWorkerDatabase(WorkerStore store, String databaseName) throws Exception {
        try (Connection c = store.openAdminJdbc(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
        JdbcInitFunctions.afterWorkerDatabaseCreated(store);
    }

    @Override public void resetWorkerData(WorkerStore store) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = store.openWorkerJdbc(); Statement s = c.createStatement()) {
            try (ResultSet rows = s.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "AND table_type = 'BASE TABLE' AND table_name NOT IN "
                            + "('flyway_schema_history','databasechangelog','databasechangeloglock')")) {
                while (rows.next()) tables.add(quoteIdentifier(rows.getString(1)));
            }
            if (!tables.isEmpty()) {
                s.execute("SET FOREIGN_KEY_CHECKS=0");
                for (String table : tables) s.execute("TRUNCATE TABLE " + table);
                s.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    @Override public void dropWorkerDatabase(WorkerStore store, String databaseName) throws SQLException {
        try (Connection c = store.openAdminJdbc(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName));
        }
    }

    @Override public void verifyDataSource(DataSource source, WorkerStore store) throws Exception {
        try (var connection = source.getConnection()) {
            if (!store.database.equals(connection.getCatalog()) || !store.jdbcUrl.equals(connection.getMetaData().getURL()))
                throw new IllegalStateException("DataSource bypasses worker database");
        }
    }

    static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
