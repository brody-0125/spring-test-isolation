package io.github.brody0125.springtestisolation.gradle.infrastructure;

import java.util.Properties;

public final class StartedInfrastructure {
    private final AutoCloseable resource;
    private final String containerId;
    private final DescriptorPublisher publisher;

    public StartedInfrastructure(AutoCloseable resource, String containerId, DescriptorPublisher publisher) {
        this.resource = resource;
        this.containerId = containerId;
        this.publisher = publisher;
    }

    public String containerId() { return containerId; }

    public void publish(Properties descriptor) { publisher.publish(descriptor); }

    public void stop() throws Exception {
        if (resource != null) resource.close();
    }

    public interface DescriptorPublisher {
        void publish(Properties descriptor);
    }
}
