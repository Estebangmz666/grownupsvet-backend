package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendConfigurationPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(FrontendConfiguration.class);

    @Test
    void absentFrontendConfigurationKeepsLinksEmptyAndCrossOriginRequestsClosed() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FrontendConfigurationProperties.class).baseUrl()).isEmpty();
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/appointments");
            request.addHeader("Origin", "https://owners.vet-demo.dev");
            request.addHeader("Access-Control-Request-Method", "GET");
            MockHttpServletResponse response = new MockHttpServletResponse();
            var source = context.getBean(UrlBasedCorsConfigurationSource.class);
            assertThat(new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        });
    }

    @Test
    void environmentBindingAcceptsTwoExactOriginsAndAnOptionalBasePath() {
        runner.withPropertyValues("grownupsvet.frontend.base-url=https://owners.vet-demo.dev/app/",
                "grownupsvet.frontend.allowed-origins=https://owners.vet-demo.dev,https://staff.vet-demo.dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(FrontendConfigurationProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://owners.vet-demo.dev/app");
                    assertThat(properties.allowedOrigins()).containsExactly("https://owners.vet-demo.dev", "https://staff.vet-demo.dev");
                });
    }

    @Test
    void localHttpCanBeConfiguredExplicitlyForDevelopment() {
        var properties = new FrontendConfigurationProperties("http://localhost:5173/", List.of("http://127.0.0.1:5174"));
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:5173");
        assertThat(properties.allowedOrigins()).containsExactly("http://127.0.0.1:5174");
    }

    @Test
    void wildcardCredentialsQueryFragmentsAndRemoteHttpAreRejectedWithoutEchoingTheInput() {
        for (String invalid : List.of("*", "https://*.vet-demo.dev", "https://user:private-value@vet-demo.dev",
                "https://vet-demo.dev?token=private-value", "https://vet-demo.dev#private-value", "http://vet-demo.dev", "https://vet-demo.dev:0")) {
            assertThatThrownBy(() -> new FrontendConfigurationProperties(invalid, List.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private-value");
        }
        assertThatThrownBy(() -> new FrontendConfigurationProperties("", List.of("https://vet-demo.dev/app")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apiOriginIsAllowedForItsOwnSwaggerWithoutOpeningOtherOrigins() {
        runner.withPropertyValues("grownupsvet.api.base-url=https://api.vet-demo.dev")
                .run(context -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
                    var configuration = context.getBean(UrlBasedCorsConfigurationSource.class).getCorsConfiguration(request);
                    assertThat(configuration.checkOrigin("https://api.vet-demo.dev")).isEqualTo("https://api.vet-demo.dev");
                    assertThat(configuration.checkOrigin("https://foreign.vet-demo.dev")).isNull();
                    assertThat(configuration.getAllowCredentials()).isFalse();
                });
    }
}
