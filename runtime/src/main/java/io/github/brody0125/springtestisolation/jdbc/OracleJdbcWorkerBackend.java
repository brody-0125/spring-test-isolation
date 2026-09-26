package io.github.brody0125.springtestisolation.jdbc;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

public final class OracleJdbcWorkerBackend implements JdbcWorkerBackend {
    public static final String ID = "oracle";

    @Override public String id() { return ID; }

    @Override public String buildWorkerJdbcUrl(WorkerStore store, String databaseName) {
        return store.infrastructureProperty(InfrastructureDescriptor.JDBC_URL);
    }

    @Override public void createWorkerDatabase(WorkerStore store, String databaseName) throws Exception {
        String password = "W1#Worker";
        store.useWorkerJdbcCredentials(databaseName, password);
        String user = userIdentifier(databaseName);
        try (Connection c = store.openAdminJdbc(); Statement s = c.createStatement()) {
            s.execute("CREATE USER " + user + " IDENTIFIED BY " + quoteIdentifier(password) + " QUOTA UNLIMITED ON USERS");
            s.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO "
                    + user);
        }
        JdbcInitFunctions.afterWorkerDatabaseCreated(store);
    }

    @Override public void resetWorkerData(WorkerStore store) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = store.openWorkerJdbc(); Statement s = c.createStatement()) {
            try (ResultSet rows = s.executeQuery(
                    "SELECT table_name FROM user_tables WHERE table_name NOT IN "
                            + "('FLYWAY_SCHEMA_HISTORY','DATABASECHANGELOG','DATABASECHANGELOGLOCK')")) {
                while (rows.next()) tables.add(quoteIdentifier(rows.getString(1)));
            }
            for (String table : tables) s.execute("TRUNCATE TABLE " + table);
        }
    }

    @Override public void dropWorkerDatabase(WorkerStore store, String databaseName) throws SQLException {
        String user = userIdentifier(databaseName);
        String sessionUser = databaseName.toUpperCase(Locale.ROOT);
        try (Connection c = store.openAdminJdbc(); Statement s = c.createStatement()) {
            List<String> sessions = new ArrayList<>();
            try (ResultSet rows = s.executeQuery(
                    "SELECT sid, serial# FROM v$session WHERE username = " + quoteLiteral(sessionUser))) {
                while (rows.next()) sessions.add(rows.getInt(1) + "," + rows.getInt(2));
            }
            for (String session : sessions) {
                try { s.execute("ALTER SYSTEM KILL SESSION '" + session + "' IMMEDIATE"); }
                catch (SQLException ignored) { /* session already gone */ }
            }
            s.execute("DROP USER " + user + " CASCADE");
        }
    }

    @Override public void verifyDataSource(DataSource source, WorkerStore store) throws Exception {
        try (var connection = source.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM dual")) {
            if (!rows.next() || !store.database.equalsIgnoreCase(rows.getString(1)))
                throw new IllegalStateException("DataSource bypasses worker database");
        }
    }

    static String userIdentifier(String databaseName) {
        if (!databaseName.matches("[A-Za-z][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("Invalid Oracle username: " + databaseName);
        return databaseName;
    }

    static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
