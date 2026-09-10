package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class UserSessionRevocationHttpIntegrationTests {
    private static final String RAW_PASSWORD = "Una frase privada para cerrar sesión";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RevokedAccessTokenRepository revokedAccessTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private String token;

    @BeforeEach
    void createUserAndSession() throws Exception {
        user = userRepository.saveAndFlush(new User("logout+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(RAW_PASSWORD), UserRole.ADMINISTRATOR));
        token = login();
    }

    @AfterEach
    void removeTestData() {
        revokedAccessTokenRepository.deleteForUser(user.getId());
        userRepository.deleteById(user.getId());
        userRepository.flush();
    }

    @Test
    void logoutPersistsRevocationAndImmediatelyRejectsTheSameToken() throws Exception {
        mockMvc.perform(delete(UserSessionController.SESSIONS_PATH + "/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(revokedAccessTokenRepository.count()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/v1/users/me/profile/photo")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(delete(UserSessionController.SESSIONS_PATH + "/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generatedContractContainsAuthenticatedLogout() throws Exception {
        JsonNode specification = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode operation = specification.path("paths")
                .path(UserSessionController.SESSIONS_PATH + "/current").path("delete");
        assertThat(operation.path("operationId").asText()).isEqualTo("deleteCurrentUserSession");
        assertThat(operation.path("security").isArray()).isTrue();
        assertThat(operation.path("responses").propertyNames()).contains("204", "401", "500");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post(UserSessionController.SESSIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", RAW_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private String bearer() { return "Bearer " + token; }
}
