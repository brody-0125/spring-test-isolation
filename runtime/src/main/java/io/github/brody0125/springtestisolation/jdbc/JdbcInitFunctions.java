package io.github.brody0125.springtestisolation.jdbc;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerStore;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import org.testcontainers.jdbc.ConnectionUrl;

/** Runs {@code TC_INITFUNCTION} from a Testcontainers JDBC URL on a worker connection. */
public final class JdbcInitFunctions {
    private JdbcInitFunctions() {}

    public static void afterWorkerDatabaseCreated(WorkerStore store) throws Exception {
        String adminUrl = store.infrastructureProperty(InfrastructureDescriptor.JDBC_URL);
        if (!JdbcWorkerUrls.hasInitFunction(adminUrl)) return;
        try (Connection connection = store.openWorkerJdbc()) {
            runIfDeclared(adminUrl, connection);
        }
    }

    public static void runIfDeclared(String adminUrl, Connection connection) throws SQLException {
        ConnectionUrl.InitFunctionDef init = JdbcWorkerUrls.initFunction(adminUrl).orElse(null);
        if (init == null) return;
        try {
            Class<?> type = Class.forName(init.getClassName());
            Method method = type.getMethod(init.getMethodName(), Connection.class);
            method.invoke(null, connection);
        } catch (ReflectiveOperationException e) {
            throw new SQLException(
                    "Failed to run TC_INITFUNCTION " + init.getClassName() + "::" + init.getMethodName(), e);
        }
    }
}
