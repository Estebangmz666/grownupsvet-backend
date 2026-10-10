package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiServerConfigurationTests {
    @Test
    void unconfiguredServerLetsSpringdocUseTheCurrentEnvironment() {
        var specification = new OpenApiConfiguration().applicationOpenApi("");
        assertThat(specification.getInfo().getVersion()).isEqualTo("0.9.0");
        assertThat(specification.getServers()).isNullOrEmpty();
    }

    @Test
    void configuredPublicBackendOverridesInternalProxyUrls() {
        var specification = new OpenApiConfiguration().applicationOpenApi("https://api.vet-demo.dev/");
        assertThat(specification.getServers()).singleElement()
                .satisfies(server -> assertThat(server.getUrl()).isEqualTo("https://api.vet-demo.dev"));
    }

    @Test
    void serverUrlNeverAcceptsCredentialsOrTokenParameters() {
        assertThatThrownBy(() -> new OpenApiConfiguration().applicationOpenApi("https://api.vet-demo.dev?token=private-value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private-value");
    }
}
