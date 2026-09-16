package io.github.brody0125.springtestisolation;

/** Application adapter for work and in-memory state that survives a test class.
 * quiesce must stop new submissions and wait for in-flight work, including scheduled work.
 * reset runs after database/Redis clearing; resume runs before the next class.
 * Implementations must be idempotent and must not resume work during reset.
 */
public interface ClassBoundary {
    void quiesce() throws Exception;
    void reset() throws Exception;
    void resume() throws Exception;
}
