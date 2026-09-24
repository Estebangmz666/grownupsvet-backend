package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grownupsvet.staff.invitations.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class StaffInvitationDisabledHttpIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private StaffInvitationService service;

    @Test
    void unconfiguredApplicationStartsAndPublicActivationReturnsSanitizedServiceUnavailable() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/account-activations")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "token", "A".repeat(43), "password", "Una frase privada para personal",
                                "confirmPassword", "Una frase privada para personal"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("STAFF_INVITATIONS_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("A".repeat(43), "Una frase privada para personal");
    }

    @Test
    void staffCreationCannotProceedWithoutTheInvitationChannel() {
        assertThatThrownBy(() -> service.createInvitation(User.pendingActivation("pending@example.test", UserRole.VETERINARIAN),
                UUID.randomUUID())).isInstanceOfSatisfying(StaffOperationException.class,
                exception -> assertThat(exception.getStatus().value()).isEqualTo(503));
    }
}
