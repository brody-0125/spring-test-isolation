package example;

import io.github.brody0125.springtestisolation.WorkerStore;

/** Separate JVM exercising allocation, hard crash (no hooks), and fresh retry. */
public class WorkerProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "create-db-failure".equals(args[0])) {
            try {
                WorkerStore.get();
                System.err.println("UNEXPECTED_ALLOCATION_SUCCESS");
                System.exit(2);
            } catch (Exception e) {
                System.exit(1);
            }
        }
        WorkerStore store = WorkerStore.get();
        try (var c = store.connection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE probe (id integer primary key)");
            s.execute("INSERT INTO probe VALUES (1)");
        }
        try (var redis = store.redis()) {
            if (redis.get("probe") != null) throw new AssertionError("Reused dirty Redis slot");
            redis.set("probe", "crashed");
        }
        System.out.println("PROBE " + store.database + " " + store.redisDatabase);
        System.out.flush();
        if (args.length > 0 && args[0].equals("crash")) Runtime.getRuntime().halt(17);
    }
}
