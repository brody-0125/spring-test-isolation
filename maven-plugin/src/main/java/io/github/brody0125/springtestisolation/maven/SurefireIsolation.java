package io.github.brody0125.springtestisolation.maven;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.LinkedHashMap;
import java.util.Map;

/** Merges Surefire settings for worker-isolated forks. */
final class SurefireIsolation {
    static final String SUREFIRE_COORDINATE = "org.apache.maven.plugins:maven-surefire-plugin";
    static final String ORDERER = "com.github.seregamorph.testsmartcontext.jupiter.SmartDirtiesClassOrderer";

    private SurefireIsolation() {}

    static Plugin requireSurefire(MavenProject project) throws MojoExecutionException {
        Plugin plugin = project.getPlugin(SUREFIRE_COORDINATE);
        if (plugin == null) {
            throw new MojoExecutionException(
                    "maven-surefire-plugin is required; spring-test-isolation maps each Surefire fork to a worker");
        }
        return plugin;
    }

    static int parseForkCount(Plugin surefire) throws MojoExecutionException {
        Xpp3Dom config = configuration(surefire);
        String raw = config.getChildValue("forkCount");
        if (raw == null || raw.isBlank()) return 1;
        if (!raw.chars().allMatch(Character::isDigit)) {
            throw new MojoExecutionException(
                    "forkCount must be a plain integer between 1 and 255 for worker isolation (got \""
                            + raw + "\"; per-core forms like 2C are unsupported)");
        }
        int forks = Integer.parseInt(raw);
        if (forks < 1 || forks > 255) {
            throw new MojoExecutionException("forkCount must be between 1 and 255 (got " + forks + ")");
        }
        return forks;
    }

    static void apply(MavenProject project, Plugin surefire, String descriptorPath, String taskName)
            throws MojoExecutionException {
        Xpp3Dom config = configuration(surefire);
        setChildValue(config, "parallel", "none");
        setChildValue(config, "threadCount", "1");
        setChildValue(config, "perCoreThreadCount", "false");
        Map<String, String> properties = requiredSystemProperties(descriptorPath, taskName);
        Xpp3Dom systemProperties = config.getChild("systemPropertyVariables");
        if (systemProperties == null) {
            systemProperties = new Xpp3Dom("systemPropertyVariables");
            config.addChild(systemProperties);
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            setChildValue(systemProperties, entry.getKey(), entry.getValue());
        }
        surefire.setConfiguration(config);
        project.getProperties().setProperty("springtestisolation.descriptor", descriptorPath);
        project.getProperties().setProperty("springtestisolation.task", taskName);
    }

    static Map<String, String> requiredSystemProperties(String descriptorPath, String taskName) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("springtestisolation.descriptor", descriptorPath);
        properties.put("springtestisolation.task", taskName);
        properties.put("junit.jupiter.execution.parallel.enabled", "false");
        properties.put("junit.jupiter.extensions.autodetection.enabled", "true");
        properties.put("junit.jupiter.testclass.order.default", ORDERER);
        properties.put("kotest.framework.parallelism", "1");
        return properties;
    }

    private static Xpp3Dom configuration(Plugin surefire) {
        Xpp3Dom config = (Xpp3Dom) surefire.getConfiguration();
        return config != null ? config : new Xpp3Dom("configuration");
    }

    private static void setChildValue(Xpp3Dom parent, String name, String value) {
        Xpp3Dom child = parent.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            parent.addChild(child);
        }
        child.setValue(value);
    }
}
