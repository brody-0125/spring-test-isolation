package io.github.brody0125.springtestisolation.jdbc;

import org.testcontainers.jdbc.ConnectionUrl;
import java.util.Optional;

/** Admin and worker JDBC URL helpers for attach mode, including Testcontainers {@code jdbc:tc:} URLs. */
public final class JdbcWorkerUrls {
    private JdbcWorkerUrls() {}

    public static boolean isTestcontainersJdbc(String url) {
        return url != null && ConnectionUrl.accepts(url);
    }

    public static void validateAdminUrl(String adminUrl) {
        if (!isTestcontainersJdbc(adminUrl)) return;
        ConnectionUrl parsed = ConnectionUrl.newInstance(adminUrl);
        if (!parsed.isInDaemonMode()) {
            throw new IllegalStateException(
                    "jdbc:tc URLs require TC_DAEMON=true so all workers attach to one container; "
                            + "without daemon mode each connection starts a new container and breaks per-worker databases");
        }
    }

    public static boolean hasInitFunction(String adminUrl) {
        return isTestcontainersJdbc(adminUrl) && ConnectionUrl.newInstance(adminUrl).getInitFunction().isPresent();
    }

    public static String withDatabaseName(String adminUrl, String databaseName) {
        if (!isTestcontainersJdbc(adminUrl)) {
            int query = adminUrl.indexOf('?');
            String suffix = query < 0 ? "" : adminUrl.substring(query);
            String base = query < 0 ? adminUrl : adminUrl.substring(0, query);
            return base.substring(0, base.lastIndexOf('/') + 1) + databaseName + suffix;
        }
        ConnectionUrl parsed = ConnectionUrl.newInstance(adminUrl);
        String hostString = parsed.getDbHostString();
        String updatedHostString = parsed.getDatabaseName()
                .map(name -> replaceLastSegment(hostString, name, databaseName))
                .orElse(swapTrailingPathSegment(hostString, databaseName));
        int protocol = adminUrl.indexOf("://");
        int query = adminUrl.indexOf('?', protocol);
        return adminUrl.substring(0, protocol + 3) + updatedHostString + (query < 0 ? "" : adminUrl.substring(query));
    }

    static String replaceLastSegment(String hostString, String previousName, String databaseName) {
        int index = hostString.lastIndexOf(previousName);
        if (index < 0) return appendDatabaseName(hostString, databaseName);
        return hostString.substring(0, index) + databaseName + hostString.substring(index + previousName.length());
    }

    static String swapTrailingPathSegment(String hostString, String databaseName) {
        int slash = hostString.lastIndexOf('/');
        if (slash >= 0 && slash < hostString.length() - 1) {
            return hostString.substring(0, slash + 1) + databaseName;
        }
        return appendDatabaseName(hostString, databaseName);
    }

    static String appendDatabaseName(String hostString, String databaseName) {
        if (hostString.isEmpty()) return databaseName;
        char last = hostString.charAt(hostString.length() - 1);
        if (last == '/' || last == ':' || last == ';') return hostString + databaseName;
        return hostString + "/" + databaseName;
    }

    public static Optional<ConnectionUrl.InitFunctionDef> initFunction(String adminUrl) {
        if (!isTestcontainersJdbc(adminUrl)) return Optional.empty();
        return ConnectionUrl.newInstance(adminUrl).getInitFunction();
    }
}
