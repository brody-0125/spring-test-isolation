package io.github.brody0125.springtestisolation.gradle.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InfrastructureProviders {
    private static final Map<String, JdbcInfrastructureProvider> JDBC = new LinkedHashMap<>();
    private static final Map<String, CacheInfrastructureProvider> CACHE = new LinkedHashMap<>();

    static {
        registerJdbc(new PostgreSqlJdbcInfrastructureProvider());
        registerJdbc(new MySqlJdbcInfrastructureProvider());
        registerCache(new RedisCacheInfrastructureProvider());
    }

    private InfrastructureProviders() {}

    public static void registerJdbc(JdbcInfrastructureProvider provider) { JDBC.put(provider.id(), provider); }

    public static void registerCache(CacheInfrastructureProvider provider) { CACHE.put(provider.id(), provider); }

    public static JdbcInfrastructureProvider jdbc(String id) {
        JdbcInfrastructureProvider provider = JDBC.get(id);
        if (provider == null) throw new IllegalArgumentException("Unsupported jdbc backend: " + id);
        return provider;
    }

    public static CacheInfrastructureProvider cache(String id) {
        CacheInfrastructureProvider provider = CACHE.get(id);
        if (provider == null) throw new IllegalArgumentException("Unsupported cache backend: " + id);
        return provider;
    }
}
