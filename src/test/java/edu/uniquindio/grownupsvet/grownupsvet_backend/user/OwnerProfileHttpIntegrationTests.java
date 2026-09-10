package edu.uniquindio.grownupsvet.grownupsvet_backend.user;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.controller.OwnerProfileController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserProfilePhotoRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class OwnerProfileHttpIntegrationTests {
    private static final String RAW_PASSWORD = "Una frase privada para el perfil";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerProfileRepository ownerProfileRepository;
    @Autowired private UserProfilePhotoRepository userProfilePhotoRepository;
    @Autowired private RevokedAccessTokenRepository revokedAccessTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User owner;
    private String token;

    @BeforeEach
    void createOwner() throws Exception {
        String email = "profile-test+" + UUID.randomUUID() + "@example.com";
        owner = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(RAW_PASSWORD), UserRole.OWNER));
        ownerProfileRepository.saveAndFlush(new OwnerProfile(owner, "María Gómez", LocalDate.of(1955, 5, 20),
                "+573001234567"));
        token = login(email, RAW_PASSWORD);
    }

    @AfterEach
    void removeTestData() {
        revokedAccessTokenRepository.deleteForUser(owner.getId());
        userProfilePhotoRepository.deleteById(owner.getId());
        ownerProfileRepository.deleteById(owner.getId());
        userRepository.deleteById(owner.getId());
        userRepository.flush();
    }

    @Test
    void readsAndUpdatesOnlyTheAllowedOwnerField() throws Exception {
        mockMvc.perform(get(OwnerProfileController.CURRENT_USER_PATH).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.fullName").value("María Gómez"))
                .andExpect(jsonPath("$.phoneNumber").value("+573001234567"))
                .andExpect(jsonPath("$.profilePhotoUrl").isEmpty());

        MvcResult updated = mockMvc.perform(patch(OwnerProfileController.CURRENT_USER_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("phoneNumber", "+573106543210"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+573106543210"))
                .andReturn();
        saveExample("profile-success.json", updated);

        MvcResult unknownProperty = mockMvc.perform(patch(OwnerProfileController.CURRENT_USER_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+573106543210\",\"role\":\"ADMINISTRATOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_PROPERTY"))
                .andReturn();
        saveExample("profile-invalid.json", unknownProperty);
    }

    @Test
    void deactivationImmediatelyInvalidatesEveryExistingToken() throws Exception {
        mockMvc.perform(delete(OwnerProfileController.CURRENT_USER_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(owner.getId()).orElseThrow().isActive()).isFalse();
        mockMvc.perform(get(OwnerProfileController.CURRENT_USER_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        owner = userRepository.findById(owner.getId()).orElseThrow();
        owner.activate();
        userRepository.saveAndFlush(owner);
    }

    @Test
    void staffTokenCannotAccessOwnerProfileAndOldRoleSnapshotIsRejected() throws Exception {
        User veterinarian = userRepository.saveAndFlush(new User("vet+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(RAW_PASSWORD), UserRole.VETERINARIAN));
        try {
            String veterinarianToken = login(veterinarian.getEmail(), RAW_PASSWORD);
            mockMvc.perform(get(OwnerProfileController.CURRENT_USER_PATH)
                            .header(HttpHeaders.AUTHORIZATION, bearer(veterinarianToken)))
                    .andExpect(status().isForbidden());

            owner.changeRole(UserRole.ADMINISTRATOR);
            owner = userRepository.saveAndFlush(owner);
            mockMvc.perform(get(OwnerProfileController.CURRENT_USER_PATH)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        } finally {
            owner.changeRole(UserRole.OWNER);
            userRepository.saveAndFlush(owner);
            userRepository.deleteById(veterinarian.getId());
        }
    }

    @Test
    void generatedContractContainsProfileOperationsAndStrictSchema() throws Exception {
        JsonNode specification = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode path = specification.path("paths").path(OwnerProfileController.CURRENT_USER_PATH);
        assertThat(path.propertyNames()).contains("get", "patch", "delete");
        assertThat(path.path("get").path("security").isArray()).isTrue();
        JsonNode updateSchema = specification.path("components").path("schemas")
                .path("UpdateOwnerProfileRequestDTO");
        assertThat(updateSchema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(updateSchema.path("required").valueStream().map(JsonNode::asText).toList())
                .containsExactly("phoneNumber");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(UserSessionController.SESSIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private String bearer(String accessToken) { return "Bearer " + accessToken; }

    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }
}
