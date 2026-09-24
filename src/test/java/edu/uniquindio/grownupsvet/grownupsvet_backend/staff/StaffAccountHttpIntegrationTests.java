package edu.uniquindio.grownupsvet.grownupsvet_backend.staff;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real authorization and persistence; the invitation lifecycle has its own integration suite. */
@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class StaffAccountHttpIntegrationTests {
    private static final String ADMINISTRATORS_PATH = "/api/v1/administrators";
    private static final String VETERINARIANS_PATH = "/api/v1/veterinarians";
    private static final String PUBLIC_PROFILES_PATH = "/api/v1/veterinarian-profiles";
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserPermissionResolver permissions;
    @MockitoBean private StaffInvitationService invitations;
    private final Map<UserRole, String> actorTokens = new EnumMap<>(UserRole.class);
    private String emailPrefix;

    @BeforeEach
    void createIsolatedActorsForEveryRole() {
        emailPrefix = "staff-http+" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "+";
        for (UserRole role : UserRole.values()) {
            User actor = users.saveAndFlush(new User(emailPrefix + role.name().toLowerCase() + "@example.test",
                    "{argon2id}fixture-credential", role));
            actorTokens.put(role, tokens.issue(actor, permissions.resolve(role)).accessToken());
        }
    }

    @AfterEach
    void deleteOnlyThisTestsProfilesBeforeTheirActors() {
        String ownUsers = "SELECT id FROM users WHERE email LIKE ?";
        String emailPattern = emailPrefix + "%";
        jdbc.update("DELETE FROM veterinarian_diplomas WHERE qualification_id IN (SELECT id FROM veterinarian_qualifications"
                + " WHERE veterinarian_id IN (" + ownUsers + "))", emailPattern);
        jdbc.update("DELETE FROM veterinarian_qualifications WHERE veterinarian_id IN (" + ownUsers + ")", emailPattern);
        jdbc.update("DELETE FROM veterinarian_profiles WHERE user_id IN (" + ownUsers + ")", emailPattern);
        jdbc.update("DELETE FROM administrator_profiles WHERE user_id IN (" + ownUsers + ")", emailPattern);
        jdbc.update("DELETE FROM staff_invitations WHERE user_id IN (" + ownUsers + ")", emailPattern);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", emailPattern);
    }

    @Test
    void onlyTheDesignatedRoleCanCreateEachKindOfStaffWithoutInheritingOtherManagementPermissions() throws Exception {
        for (UserRole role : UserRole.values()) {
            MvcResult administrator = mvc.perform(authenticated(post(ADMINISTRATORS_PATH), role,
                            Map.of("email", emailPrefix + "new-admin-" + role.name().toLowerCase() + "@example.test",
                                    "fullName", "María Fernanda Gómez")))
                    .andExpect(status().is(role == UserRole.SUPER_ADMIN ? 201 : 403)).andReturn();
            if (role == UserRole.SUPER_ADMIN) {
                JsonNode created = body(administrator);
                assertPendingAccount(created, UserRole.ADMINISTRATOR);
                assertThat(administrator.getResponse().getHeader(HttpHeaders.LOCATION))
                        .isEqualTo(ADMINISTRATORS_PATH + "/" + created.path("id").asText());
                saveExample("staff-administrator-created.json", administrator);
            }
            MvcResult veterinarian = mvc.perform(authenticated(post(VETERINARIANS_PATH), role, veterinarianRequest()))
                    .andExpect(status().is(role == UserRole.ADMINISTRATOR ? 201 : 403)).andReturn();
            if (role == UserRole.ADMINISTRATOR) {
                JsonNode created = body(veterinarian);
                assertPendingAccount(created, UserRole.VETERINARIAN);
                assertThat(created.path("qualifications").size()).isEqualTo(1);
                assertThat(created.path("qualifications").get(0).path("isBaseDegree").asBoolean()).isTrue();
                saveExample("staff-veterinarian-created.json", veterinarian);
            }
        }
        mvc.perform(post(ADMINISTRATORS_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", emailPrefix + "anonymous@example.test", "fullName", "María Gómez"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingStaffCannotSkipActivationAndProfileUpdatesCannotChangeTheirIdentity() throws Exception {
        Map<String, Object> creation = veterinarianRequest();
        JsonNode created = createVeterinarian(creation);
        String target = VETERINARIANS_PATH + "/" + created.path("id").asText();
        UUID id = UUID.fromString(created.path("id").asText());
        mvc.perform(authenticated(patch(target + "/status"), UserRole.ADMINISTRATOR, Map.of("status", "ACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STAFF_ACTIVATION_REQUIRED"));

        Map<String, Object> update = new LinkedHashMap<>(creation);
        update.remove("email");
        update.put("fullName", "Laura Marcela Gómez");
        for (String forbiddenField : List.of("email", "role", "dateOfBirth", "password")) {
            Map<String, Object> rejected = new LinkedHashMap<>(update);
            rejected.put(forbiddenField, "PRIVATE_REJECTED_INPUT");
            mvc.perform(authenticated(put(target), UserRole.ADMINISTRATOR, rejected)).andExpect(status().isBadRequest());
        }
        mvc.perform(authenticated(put(target), UserRole.ADMINISTRATOR, update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Laura Marcela Gómez"))
                .andExpect(jsonPath("$.email").value(creation.get("email")))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
        mvc.perform(authenticated(patch(target + "/status"), UserRole.ADMINISTRATOR, Map.of("status", "PENDING_ACTIVATION")))
                .andExpect(status().isBadRequest());
        mvc.perform(authenticated(patch(target + "/status"), UserRole.ADMINISTRATOR, Map.of("status", "DISABLED")))
                .andExpect(status().isOk());
        mvc.perform(authenticated(patch(target + "/status"), UserRole.ADMINISTRATOR, Map.of("status", "ACTIVE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STAFF_ACTIVATION_REQUIRED"));
        User persisted = users.findById(id).orElseThrow();
        assertThat(persisted.getEmail()).isEqualTo(creation.get("email"));
        assertThat(persisted.getRole()).isEqualTo(UserRole.VETERINARIAN);
        assertThat(persisted.getPasswordHash()).isNull();
    }

    @Test
    void ownersSeeOnlyActiveProfessionalProfilesAndNeverPrivateLoginData() throws Exception {
        JsonNode created = createVeterinarian(veterinarianRequest());
        JsonNode pending = createVeterinarian(veterinarianRequest());
        String id = created.path("id").asText();
        String publicPath = PUBLIC_PROFILES_PATH + "/" + id;
        mvc.perform(authenticated(get(publicPath), UserRole.OWNER)).andExpect(status().isNotFound());

        User veterinarian = users.findById(UUID.fromString(id)).orElseThrow();
        veterinarian.activateWithPasswordHash("{argon2id}established-fixture-password");
        users.saveAndFlush(veterinarian);
        MvcResult visible = mvc.perform(authenticated(get(publicPath), UserRole.OWNER))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn();
        JsonNode professionalProfile = body(visible);
        assertThat(professionalProfile.propertyNames())
                .doesNotContain("email", "dateOfBirth", "password", "passwordHash", "role", "status");
        assertThat(visible.getResponse().getContentAsString()).doesNotContain(veterinarian.getEmail());
        assertThat(professionalProfile.path("professionalPhoneNumber").asText()).isEqualTo("+573001234567");
        assertThat(professionalProfile.path("qualifications").get(0).path("isBaseDegree").asBoolean()).isTrue();
        saveExample("staff-veterinarian-public-profile.json", visible);
        JsonNode directory = body(mvc.perform(authenticated(get(PUBLIC_PROFILES_PATH), UserRole.OWNER))
                .andExpect(status().isOk()).andReturn());
        assertThat(directory.path("items").valueStream().map(item -> item.path("id").asText()).toList())
                .contains(id).doesNotContain(pending.path("id").asText());
        for (UserRole role : List.of(UserRole.ADMINISTRATOR, UserRole.VETERINARIAN, UserRole.SUPER_ADMIN)) {
            mvc.perform(authenticated(get(publicPath), role)).andExpect(status().isForbidden());
        }
        mvc.perform(get(publicPath)).andExpect(status().isUnauthorized());
        mvc.perform(authenticated(patch(VETERINARIANS_PATH + "/" + id + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "DISABLED"))).andExpect(status().isOk());
        mvc.perform(authenticated(get(publicPath), UserRole.OWNER)).andExpect(status().isNotFound());
    }

    @Test
    void generatedContractPublishesTheActualStaffRoutesAndPreservesPrivateIdentityBoundaries() throws Exception {
        JsonNode specification = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        JsonNode paths = specification.path("paths");
        assertThat(paths.propertyNames()).contains(ADMINISTRATORS_PATH, ADMINISTRATORS_PATH + "/{id}",
                ADMINISTRATORS_PATH + "/{id}/status", VETERINARIANS_PATH, VETERINARIANS_PATH + "/{id}",
                VETERINARIANS_PATH + "/{id}/status", VETERINARIANS_PATH + "/{veterinarianId}/qualifications",
                VETERINARIANS_PATH + "/{veterinarianId}/qualifications/{qualificationId}",
                VETERINARIANS_PATH + "/{veterinarianId}/qualifications/{qualificationId}/diploma",
                PUBLIC_PROFILES_PATH, PUBLIC_PROFILES_PATH + "/{veterinarianId}",
                PUBLIC_PROFILES_PATH + "/{veterinarianId}/photo",
                PUBLIC_PROFILES_PATH + "/{veterinarianId}/qualifications/{qualificationId}/diploma");
        for (String path : List.of(ADMINISTRATORS_PATH, VETERINARIANS_PATH, PUBLIC_PROFILES_PATH)) {
            assertThat(paths.path(path).path("get").path("security").isArray()).isTrue();
        }
        JsonNode schemas = specification.path("components").path("schemas");
        assertThat(schemas.path("CreateAdministratorRequestDTO").path("properties").propertyNames())
                .containsExactlyInAnyOrder("email", "fullName");
        assertThat(schemas.path("UpdateAdministratorRequestDTO").path("properties").propertyNames())
                .containsExactly("fullName");
        assertThat(schemas.path("UpdateVeterinarianRequestDTO").path("properties").propertyNames())
                .doesNotContain("email", "role", "status", "dateOfBirth", "password");
        assertThat(schemas.path("VeterinarianPublicProfileResponseDTO").path("properties").propertyNames())
                .doesNotContain("email", "role", "status", "dateOfBirth", "passwordHash");
        assertThat(schemas.path("AdministratorResponseDTO").path("properties").path("status").path("enum")
                .valueStream().map(JsonNode::asText).toList())
                .contains("PENDING_ACTIVATION", "ACTIVE", "DISABLED");
        for (String schemaName : List.of("AdministratorPageResponseDTO", "VeterinarianPageResponseDTO",
                "VeterinarianPublicProfilePageResponseDTO")) {
            for (String field : List.of("page", "size", "totalElements", "totalPages")) {
                assertThat(schemas.path(schemaName).path("properties").path(field).path("type").asText())
                        .as("%s.%s must describe the integer returned over HTTP", schemaName, field).isEqualTo("integer");
            }
        }
        JsonNode qualification = schemas.path("VeterinarianQualificationResponseDTO").path("properties");
        for (String field : List.of("isBaseDegree", "diplomaAvailable", "diplomaPublished")) {
            assertThat(qualification.path(field).path("type").asText()).as(field).isEqualTo("boolean");
        }
        assertThat(qualification.path("type").path("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactlyInAnyOrder("UNDERGRADUATE", "SPECIALIZATION", "MASTERS", "DOCTORATE");
    }

    private JsonNode createVeterinarian(Map<String, Object> request) throws Exception {
        return body(mvc.perform(authenticated(post(VETERINARIANS_PATH), UserRole.ADMINISTRATOR, request))
                .andExpect(status().isCreated()).andReturn());
    }

    private Map<String, Object> veterinarianRequest() {
        return new LinkedHashMap<>(Map.of("email", emailPrefix
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "@example.test",
                "fullName", "Laura Marcela Ramírez", "professionalPhoneNumber", "+573001234567",
                "professionalRegistrationNumber", "MV-" + UUID.randomUUID(),
                "baseDegreeTitle", "Medicina veterinaria", "baseDegreeInstitution", "Universidad de Ejemplo",
                "biography", "Atención clínica de animales de compañía."));
    }

    private void assertPendingAccount(JsonNode response, UserRole expectedRole) {
        assertThat(response.path("status").asText()).isEqualTo("PENDING_ACTIVATION");
        assertThat(response.propertyNames()).doesNotContain("password", "passwordHash", "dateOfBirth");
        User persisted = users.findById(UUID.fromString(response.path("id").asText())).orElseThrow();
        assertThat(persisted.getRole()).isEqualTo(expectedRole);
        assertThat(persisted.getPasswordHash()).isNull();
        assertThat(persisted.isActive()).isFalse();
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UserRole role) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actorTokens.get(role));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UserRole role, Object body) {
        return authenticated(request, role).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }

    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }
}
