package edu.uniquindio.grownupsvet.grownupsvet_backend.staff;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service.StaffInvitationService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.*;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class VeterinarianQualificationsHttpIntegrationTests {
    private static final String PASSWORD = "Una frase privada para mis diplomas";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired UserRepository users;
    @Autowired OwnerProfileRepository owners;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffInvitationService invitations;
    private final List<UUID> accountIds = new ArrayList<>();
    private String administratorToken;
    private String ownerToken;
    private UUID veterinarianId;
    private UUID baseDegreeId;

    @BeforeEach
    void setup() throws Exception {
        administratorToken = login(account(UserRole.ADMINISTRATOR));
        ownerToken = login(account(UserRole.OWNER));
        JsonNode veterinarian = createVeterinarian("REG-" + UUID.randomUUID().toString().replace("-", ""));
        veterinarianId = UUID.fromString(veterinarian.path("id").asText());
        baseDegreeId = UUID.fromString(veterinarian.path("qualifications").get(0).path("id").asText());
        User veterinarianAccount = users.findById(veterinarianId).orElseThrow();
        veterinarianAccount.activateWithPasswordHash(encoder.encode(PASSWORD));
        users.saveAndFlush(veterinarianAccount);
    }

    @AfterEach
    void cleanupOnlyCreatedAccounts() {
        for (UUID id : accountIds) {
            jdbc.update("DELETE FROM veterinarian_diplomas WHERE qualification_id IN (SELECT id FROM veterinarian_qualifications WHERE veterinarian_id = ?)", id);
            jdbc.update("DELETE FROM veterinarian_qualifications WHERE veterinarian_id = ?", id);
            jdbc.update("DELETE FROM veterinarian_profiles WHERE user_id = ?", id);
            jdbc.update("DELETE FROM administrator_profiles WHERE user_id = ?", id);
            jdbc.update("DELETE FROM owner_profiles WHERE user_id = ?", id);
        }
        for (UUID id : accountIds) { jdbc.update("DELETE FROM users WHERE id = ?", id); }
    }

    @Test
    void baseDegreeCannotBeDeletedAndAdditionalQualificationHasAWorkingLocation() throws Exception {
        mvc.perform(delete(qualificationPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BASE_DEGREE_REQUIRED"));
        MvcResult creation = mvc.perform(post(qualificationsPath()).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(qualificationJson("Medicina Interna", 2020)))
                .andExpect(status().isCreated()).andReturn();
        String location = creation.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotBlank();
        mvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Medicina Interna"));
        mvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicationControlsOwnerDownloadAndAcademicEditsRequireNewReview() throws Exception {
        byte[] bytes = pdf();
        upload(baseDegreeId, bytes, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.diplomaAvailable").value(true)).andExpect(jsonPath("$.diplomaPublished").value(false));
        mvc.perform(get(publicDiplomaPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/veterinarian-profiles/" + veterinarianId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.qualifications[0].diplomaAvailable").value(false));
        publish(baseDegreeId, true).andExpect(status().isOk());
        mvc.perform(get(publicDiplomaPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diploma.pdf\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(put(qualificationPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNDERGRADUATE\",\"title\":\"Medicina Veterinaria y Zootecnia\",\"institution\":\"Universidad del Quindío\",\"graduationYear\":2020}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diplomaPublished").value(false));
        mvc.perform(get(publicDiplomaPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get(qualificationPath(baseDegreeId) + "/diploma").header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }

    @Test
    void qualificationOwnershipActiveStateAndRoleAreCheckedOnEveryFileAccess() throws Exception {
        upload(baseDegreeId, pdf(), true).andExpect(status().isOk());
        JsonNode another = createVeterinarian("REG-" + UUID.randomUUID().toString().replace("-", ""));
        UUID anotherId = UUID.fromString(another.path("id").asText());
        mvc.perform(get("/api/v1/veterinarians/" + anotherId + "/qualifications/" + baseDegreeId + "/diploma")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get(qualificationPath(baseDegreeId) + "/diploma").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(qualificationPath(baseDegreeId) + "/diploma").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/veterinarians/" + veterinarianId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(publicDiplomaPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/veterinarian-profiles/" + veterinarianId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingDiplomaPreservesQualificationAndCannotPublishMissingFile() throws Exception {
        upload(baseDegreeId, pdf(), true).andExpect(status().isOk());
        mvc.perform(delete(qualificationPath(baseDegreeId) + "/diploma").header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get(qualificationPath(baseDegreeId)).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diplomaAvailable").value(false))
                .andExpect(jsonPath("$.diplomaPublished").value(false));
        publish(baseDegreeId, true).andExpect(status().isNotFound());
    }

    @Test
    void rejectsFutureGraduationAndUniqueRegistrationConflictsWithoutSqlDetails() throws Exception {
        mvc.perform(post(qualificationsPath()).header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(qualificationJson("Especialización", 9999)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_GRADUATION_YEAR"));
        String existingRegistration = jdbc.queryForObject("SELECT professional_registration_number FROM veterinarian_profiles WHERE user_id = ?", String.class, veterinarianId);
        MvcResult duplicate = mvc.perform(post("/api/v1/veterinarians").header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(veterinarianJson(existingRegistration.toLowerCase(Locale.ROOT))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("PROFESSIONAL_REGISTRATION_ALREADY_EXISTS")).andReturn();
        assertThat(duplicate.getResponse().getContentAsString()).doesNotContain("uk_veterinarian", "23505", "INSERT INTO");
    }

    private User account(UserRole role) {
        User user = users.saveAndFlush(new User("qualification+" + UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD), role));
        accountIds.add(user.getId());
        if (role == UserRole.OWNER) {
            owners.saveAndFlush(new OwnerProfile(user, "María Gómez", LocalDate.of(1955, 5, 20), "+573001234567"));
        }
        return user;
    }
    private String login(User user) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    private JsonNode createVeterinarian(String registration) throws Exception {
        JsonNode response = mapper.readTree(mvc.perform(post("/api/v1/veterinarians").header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(veterinarianJson(registration)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        accountIds.add(UUID.fromString(response.path("id").asText()));
        return response;
    }
    private String veterinarianJson(String registration) throws Exception {
        return mapper.writeValueAsString(Map.of("email", "vet-qualification+" + UUID.randomUUID() + "@example.com",
                "fullName", "Laura Gómez", "professionalPhoneNumber", "+573001234567", "professionalRegistrationNumber", registration,
                "baseDegreeTitle", "Medicina Veterinaria", "baseDegreeInstitution", "Universidad del Quindío"));
    }
    private String qualificationJson(String title, int year) throws Exception {
        return mapper.writeValueAsString(Map.of("type", "SPECIALIZATION", "title", title, "institution", "Universidad del Quindío", "graduationYear", year));
    }
    private ResultActions upload(UUID qualificationId, byte[] bytes, boolean published) throws Exception {
        return mvc.perform(multipart(qualificationPath(qualificationId) + "/diploma")
                .file(new MockMultipartFile("file", "diploma.pdf", MediaType.APPLICATION_PDF_VALUE, bytes))
                .param("published", Boolean.toString(published)).with(request -> { request.setMethod("PUT"); return request; })
                .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)));
    }
    private ResultActions publish(UUID qualificationId, boolean published) throws Exception {
        return mvc.perform(patch(qualificationPath(qualificationId) + "/diploma/publication")
                .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":" + published + "}"));
    }
    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); return output.toByteArray();
        }
    }
    private String qualificationsPath() { return "/api/v1/veterinarians/" + veterinarianId + "/qualifications"; }
    private String qualificationPath(UUID qualificationId) { return qualificationsPath() + "/" + qualificationId; }
    private String publicDiplomaPath(UUID qualificationId) { return "/api/v1/veterinarian-profiles/" + veterinarianId + "/qualifications/" + qualificationId + "/diploma"; }
    private String bearer(String token) { return "Bearer " + token; }
}
