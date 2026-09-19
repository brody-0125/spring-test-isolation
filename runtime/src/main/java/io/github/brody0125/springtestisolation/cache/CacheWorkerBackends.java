package io.github.brody0125.springtestisolation.cache;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class CacheWorkerBackends {
    private static final Map<String, CacheWorkerBackend> REGISTERED = new LinkedHashMap<>();

    static {
        register(new RedisCacheWorkerBackend());
        register(new NoneCacheWorkerBackend());
    }

    private CacheWorkerBackends() {}

    public static void register(CacheWorkerBackend backend) {
        REGISTERED.put(backend.id(), backend);
    }

    public static CacheWorkerBackend resolve(Properties descriptor) {
        String id = descriptor.getProperty(InfrastructureDescriptor.CACHE_BACKEND, InfrastructureDescriptor.DEFAULT_CACHE_BACKEND);
        CacheWorkerBackend backend = REGISTERED.get(id);
        if (backend == null) throw new IllegalStateException("Unsupported cache.backend: " + id);
        return backend;
    }
}
