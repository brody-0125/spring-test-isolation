package example;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcTcInit {
    private JdbcTcInit() {}

    public static void schema(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        try (Statement statement = connection.createStatement()) {
            if (product.contains("oracle")) {
                statement.execute("""
                        BEGIN
                          EXECUTE IMMEDIATE 'CREATE TABLE tc_init_marker (id NUMBER PRIMARY KEY, worker VARCHAR2(255) NOT NULL)';
                        EXCEPTION
                          WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
                        END""");
            } else {
                statement.execute("CREATE TABLE IF NOT EXISTS tc_init_marker (id INT PRIMARY KEY, worker VARCHAR(255) NOT NULL)");
            }
        }
    }
}
