package io.github.brody0125.springtestisolation.maven;

import io.github.brody0125.springtestisolation.InfrastructureDescriptor;
import io.github.brody0125.springtestisolation.WorkerDescriptorFiles;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Path;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES, threadSafe = true)
public class ConfigureMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "springtestisolation.jdbcBackend", defaultValue = InfrastructureDescriptor.DEFAULT_JDBC_BACKEND)
    private String jdbcBackend;

    @Parameter(property = "springtestisolation.cacheBackend", defaultValue = InfrastructureDescriptor.DEFAULT_CACHE_BACKEND)
    private String cacheBackend;

    @Parameter(property = "springtestisolation.maxCacheSlots", defaultValue = "255")
    private int maxCacheSlots;

    @Override
    public void execute() throws MojoExecutionException {
        Plugin surefire = SurefireIsolation.requireSurefire(project);
        int forks = SurefireIsolation.parseForkCount(surefire);
        if (forks > maxCacheSlots) {
            throw new MojoExecutionException(
                    "forkCount (" + forks + ") cannot exceed springtestisolation.maxCacheSlots (" + maxCacheSlots + ")");
        }
        try {
            Path directory = Path.of(project.getBuild().getDirectory(), "spring-test-isolation", "worker");
            Path descriptor = WorkerDescriptorFiles.writeBase(directory, jdbcBackend, cacheBackend, maxCacheSlots);
            String descriptorPath = descriptor.toAbsolutePath().toString();
            String taskName = project.getGroupId() + ":" + project.getArtifactId();
            SurefireIsolation.apply(surefire, descriptorPath, taskName);
            getLog().info("spring-test-isolation descriptor at " + descriptorPath
                    + " for " + forks + " Surefire fork(s); containers are not started by this plugin");
        } catch (IllegalArgumentException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
