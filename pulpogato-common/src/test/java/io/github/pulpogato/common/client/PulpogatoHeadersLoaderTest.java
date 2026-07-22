package io.github.pulpogato.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PulpogatoHeadersLoaderTest {

    @Test
    void loadsPulpogatoVersionHeaderFromClasspathProperties() {
        assertThat(PulpogatoHeadersLoader.loadHeaders()).containsEntry("X-Pulpogato-Version", "1.0.0-test");
    }

    @Test
    void loadsGithubApiVersionHeaderFromClasspathProperties() {
        assertThat(PulpogatoHeadersLoader.loadHeaders()).containsEntry("X-GitHub-Api-Version", "2022-11-28-test");
    }
}
