package io.github.brody0125.springtestisolation.jdbc;

import io.github.brody0125.springtestisolation.WorkerStore;
import javax.sql.DataSource;

/** Per-worker JDBC lifecycle for one shared container per build. */
public interface JdbcWorkerBackend {
    String id();

    String buildWorkerJdbcUrl(WorkerStore store, String databaseName);

    void createWorkerDatabase(WorkerStore store, String databaseName) throws Exception;

    void resetWorkerData(WorkerStore store) throws Exception;

    void dropWorkerDatabase(WorkerStore store, String databaseName) throws Exception;

    void verifyDataSource(DataSource source, WorkerStore store) throws Exception;
}
