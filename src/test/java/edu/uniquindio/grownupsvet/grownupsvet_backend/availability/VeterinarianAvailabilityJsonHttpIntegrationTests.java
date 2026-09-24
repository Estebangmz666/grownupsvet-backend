package edu.uniquindio.grownupsvet.grownupsvet_backend.availability;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
@Transactional
class VeterinarianAvailabilityJsonHttpIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private VeterinarianProfileRepository veterinarians;
    @Autowired private JwtTokenService tokens;
    @Autowired private UserPermissionResolver permissions;
    @Autowired private Clock clock;

    private UUID veterinarianId;
    private String authorization;
    private LocalDate date;

    @BeforeEach
    void createFictionalAdministratorAndVeterinarian() {
        User administrator = users.saveAndFlush(new User("availability-json-admin+" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", UserRole.ADMINISTRATOR));
        User veterinarian = users.saveAndFlush(new User("availability-json-vet+" + UUID.randomUUID() + "@example.test",
                "{argon2id}fixture-only", UserRole.VETERINARIAN));
        veterinarianId = veterinarian.getId();
        veterinarians.saveAndFlush(new VeterinarianProfile(veterinarian, "Laura Ramírez", "+573001234567",
                "JSON-" + veterinarianId, null, administrator.getId(), clock.instant()));
        authorization = "Bearer " + tokens.issue(administrator, permissions.resolve(UserRole.ADMINISTRATOR)).accessToken();
        date = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).plusDays(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1791208800", "1791208800.5", "true", "[]", "{}", "\"2026-10-05T09:00:00\""})
    void rejectsNonIsoOffsetInputsBeforeCreatingAnySlot(String dateJson) throws Exception {
        assertTypeMismatch(post(schedulePath()), "{\"startsAt\":" + dateJson + "}");
        assertPersistedCounts(0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.9", "1.0", "1e0", "\"1\"", "\"\"", "true"})
    void rejectsWeekdayCoercionBeforeGeneratingAnySlots(String weekdayJson) throws Exception {
        String body = "{\"startDate\":\"" + date + "\",\"endDate\":\"" + date
                + "\",\"daysOfWeek\":[" + weekdayJson + "],\"dailyStartTime\":\"09:00\",\"dailyEndTime\":\"10:00\"}";
        assertTypeMismatch(post("/api/v1/veterinarians/" + veterinarianId + "/availability-slot-batches"), body);
        assertPersistedCounts(0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.9", "0.0", "0e0", "\"0\"", "\"\"", "false"})
    void rejectsVersionCoercionWithoutMutatingTheTurnOrItsHistory(String versionJson) throws Exception {
        JsonNode created = createSlot();
        String resource = schedulePath() + "/" + created.path("id").asText();
        assertTypeMismatch(patch(resource + "/status"), "{\"status\":\"BLOCKED\",\"expectedVersion\":"
                + versionJson + ",\"reason\":\"Ajuste de agenda\"}");
        assertTypeMismatch(put(resource), "{\"startsAt\":\"" + date + "T10:00:00-05:00\",\"expectedVersion\":"
                + versionJson + ",\"reason\":\"Ajuste de agenda\"}");
        assertPersistedCounts(1, 1);
        assertThat(jdbc.queryForObject("SELECT version FROM veterinarian_availability_slots WHERE veterinarian_id = ?",
                Long.class, veterinarianId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM veterinarian_availability_slots WHERE veterinarian_id = ?",
                String.class, veterinarianId)).isEqualTo("PUBLISHED");
    }

    @Test
    void acceptsIntegerVersionsAndIsoOffsetsWithoutChangingTheRepresentedTime() throws Exception {
        JsonNode created = createSlot();
        assertThat(created.path("startsAt").asText()).isEqualTo(date + "T14:00:00Z");
        mvc.perform(request(patch(schedulePath() + "/" + created.path("id").asText() + "/status"),
                        "{\"status\":\"BLOCKED\",\"expectedVersion\":0,\"reason\":\"Ajuste de agenda\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertPersistedCounts(1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"09:00:01", "09:00:00.000000001"})
    void rejectsOffGridSecondsAndFractionsWithoutCreatingSlots(String time) throws Exception {
        mvc.perform(request(post(schedulePath()), "{\"startsAt\":\"" + date + "T" + time + "-05:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY_REQUEST"));
        assertPersistedCounts(0, 0);
    }

    private JsonNode createSlot() throws Exception {
        String response = mvc.perform(request(post(schedulePath()), "{\"startsAt\":\"" + date + "T09:00:00-05:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertTypeMismatch(MockHttpServletRequestBuilder request, String body) throws Exception {
        mvc.perform(request(request, body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder request, String body) {
        return request.header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String schedulePath() {
        return "/api/v1/veterinarians/" + veterinarianId + "/availability-slots";
    }

    private void assertPersistedCounts(long slotCount, long eventCount) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM veterinarian_availability_slots WHERE veterinarian_id = ?",
                Long.class, veterinarianId)).isEqualTo(slotCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM veterinarian_availability_events event "
                        + "JOIN veterinarian_availability_slots slot ON slot.id = event.slot_id WHERE slot.veterinarian_id = ?",
                Long.class, veterinarianId)).isEqualTo(eventCount);
    }
}
