package io.github.brody0125.springtestisolation.maven;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurefireIsolationTest {
    @Test
    void rejectsPerCoreForkCount() {
        Plugin plugin = new Plugin();
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom forkCount = new Xpp3Dom("forkCount");
        forkCount.setValue("2C");
        config.addChild(forkCount);
        plugin.setConfiguration(config);
        assertThrows(MojoExecutionException.class, () -> SurefireIsolation.parseForkCount(plugin));
    }

    @Test
    void defaultsForkCountToOne() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        plugin.setConfiguration(new Xpp3Dom("configuration"));
        assertEquals(1, SurefireIsolation.parseForkCount(plugin));
    }
}
