package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery;

import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grownupsvet.security.password-recovery.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class PasswordRecoveryDisabledHttpIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @Test
    void disabledDeliveryReturnsServiceUnavailableForExistingAndUnknownAccounts() throws Exception {
        User user = users.saveAndFlush(new User("disabled-recovery+" + UUID.randomUUID() + "@example.com",
                encoder.encode("Una frase para esta cuenta de prueba"), UserRole.ADMINISTRATOR));
        try {
            for (String email : new String[]{user.getEmail(), "unknown@example.com"}) {
                String body = mvc.perform(post("/api/v1/auth/password-recoveries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("email", email))))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.errorCode").value("PASSWORD_RECOVERY_UNAVAILABLE"))
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(email, "SMTP", "password_hash");
            }
        } finally {
            users.deleteById(user.getId());
            users.flush();
        }
    }
}
