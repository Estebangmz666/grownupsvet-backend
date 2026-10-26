package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.exception.ApiErrorResponseFactory;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.controller.HelloController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.shared.security.ApiSecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CorsAndProbeHttpTests.ProbeTestController.class, HelloController.class}, properties = {
        "grownupsvet.frontend.base-url=",
        "grownupsvet.frontend.allowed-origins=https://owners.vet-demo.dev,https://staff.vet-demo.dev",
        "grownupsvet.api.base-url="})
@Import({SecurityConfiguration.class, FrontendConfiguration.class, ApiSecurityErrorHandler.class, ApiErrorResponseFactory.class,
        CorsAndProbeHttpTests.ProbeTestController.class, HelloController.class})
class CorsAndProbeHttpTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void publicHelloReturnsExactPlainTextWithoutAuthenticationOrOrigin() throws Exception {
        mvc.perform(get(HelloController.HELLO_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("text/plain;charset=UTF-8")))
                .andExpect(content().string("Hola Mundo, GrownUpsVet desde google cloud"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(decoder);
    }

    @Test
    void helloPublicAccessIsLimitedToItsExactGetRoute() throws Exception {
        mvc.perform(post(HelloController.HELLO_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(HelloController.HELLO_PATH + "/private")).andExpect(status().isUnauthorized());
        verifyNoInteractions(decoder);
    }

    @Test
    void permittedPreflightIsHandledBeforeJwtAuthenticationForBothPortals() throws Exception {
        for (String origin : new String[]{"https://owners.vet-demo.dev", "https://staff.vet-demo.dev"}) {
            mvc.perform(options("/api/v1/appointments")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
        verifyNoInteractions(decoder);
    }

    @Test
    void foreignOriginsAndUnsupportedMethodsCannotUsePreflight() throws Exception {
        mvc.perform(options("/api/v1/appointments")
                        .header(HttpHeaders.ORIGIN, "https://foreign.vet-demo.dev")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(options("/api/v1/appointments")
                        .header(HttpHeaders.ORIGIN, "https://owners.vet-demo.dev")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "TRACE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(decoder);
    }

    @Test
    void allowedCorsOriginDoesNotGrantAccessToProtectedResources() throws Exception {
        mvc.perform(get("/test/protected").header(HttpHeaders.ORIGIN, "https://owners.vet-demo.dev"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://owners.vet-demo.dev"))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void onlyMinimalGetHealthProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(options("/actuator/health/liveness")).andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProbeTestController {
        @GetMapping({"/actuator/health/liveness", "/actuator/health/readiness"})
        Map<String, String> status() { return Map.of("status", "UP"); }
        @GetMapping("/test/protected")
        Map<String, String> protectedResource() { return Map.of("status", "private"); }
    }
}
