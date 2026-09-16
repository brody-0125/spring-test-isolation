package io.github.brody0125.springtestisolation;

import java.util.concurrent.*;

/** A stuck application hook poisons the worker instead of hanging the suite forever. */
final class BoundaryCalls {
    enum Phase { QUIESCE, RESET, RESUME }
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spring-test-isolation-boundary");
        thread.setDaemon(true);
        return thread;
    });
    static void invoke(Phase phase) throws Exception {
        long timeout = Long.getLong("springtestisolation.boundaryTimeoutMillis", 10_000L);
        if (timeout <= 0) throw new IllegalArgumentException("boundaryTimeoutMillis must be positive");
        var hooks = BoundaryState.hooks();
        Future<?> future = executor.submit(() -> {
            for (ClassBoundary hook : hooks) {
                try {
                    switch (phase) {
                        case QUIESCE -> hook.quiesce();
                        case RESET -> hook.reset();
                        case RESUME -> hook.resume();
                    }
                } catch (Exception e) { throw new CompletionException(e); }
            }
        });
        try { future.get(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("Boundary hook timed out: " + phase, e);
        } catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt(); throw e;
        } catch (ExecutionException e) { throw new IllegalStateException("Boundary hook failed: " + phase, e.getCause()); }
    }
    private BoundaryCalls() {}
}
