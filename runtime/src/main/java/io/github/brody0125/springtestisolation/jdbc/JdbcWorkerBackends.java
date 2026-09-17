package io.github.brody0125.springtestisolation.jdbc;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class JdbcWorkerBackends {
    private static final Map<String, JdbcWorkerBackend> REGISTERED = new LinkedHashMap<>();

    static {
        register(new PostgreSqlJdbcWorkerBackend());
    }

    private JdbcWorkerBackends() {}

    public static void register(JdbcWorkerBackend backend) {
        REGISTERED.put(backend.id(), backend);
    }

    public static JdbcWorkerBackend resolve(Properties descriptor) {
        String id = descriptor.getProperty(InfrastructureDescriptor.JDBC_BACKEND, InfrastructureDescriptor.DEFAULT_JDBC_BACKEND);
        JdbcWorkerBackend backend = REGISTERED.get(id);
        if (backend == null) throw new IllegalStateException("Unsupported jdbc.backend: " + id);
        return backend;
    }
}
