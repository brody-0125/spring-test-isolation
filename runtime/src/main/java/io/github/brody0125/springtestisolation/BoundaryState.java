package io.github.brody0125.springtestisolation;

import org.springframework.context.ConfigurableApplicationContext;
import java.util.*;

final class BoundaryState {
    static final Set<ConfigurableApplicationContext> contexts = Collections.newSetFromMap(new IdentityHashMap<>());
    static Class<?> running;
    static List<ClassBoundary> hooks() {
        contexts.removeIf(c -> !c.isActive());
        return contexts.stream().flatMap(c -> c.getBeansOfType(ClassBoundary.class).values().stream()).toList();
    }
    private BoundaryState() {}
}
