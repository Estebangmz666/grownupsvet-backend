package edu.uniquindio.grownupsvet.grownupsvet_backend.availability;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service.JwtTokenService;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.QualificationType;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.model.VeterinarianQualification;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.repository.VeterinarianQualificationRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
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

@SpringBootTest(properties = {"grownupsvet.staff.bootstrap.enabled=false", "grownupsvet.staff.invitations.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
@Transactional
class VeterinarianAvailabilityHttpIntegrationTests {
    private static final String BASE = "/api/v1/veterinarians";
    private static final String OWNER_BASE = "/api/v1/availability-slots";

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private VeterinarianProfileRepository veterinarianProfiles;
    @Autowired private VeterinarianQualificationRepository qualifications;
    @Autowired private JwtTokenService tokenService;
    @Autowired private UserPermissionResolver permissionResolver;
    @Autowired private Clock clock;

    private final Map<UserRole, User> actors = new EnumMap<>(UserRole.class);
    private final Map<UserRole, String> tokens = new EnumMap<>(UserRole.class);
    private User veterinarian;
    private LocalDate appointmentDate;

    @BeforeEach
    void createFictionalActorsAndVeterinarianProfile() {
        for (UserRole role : UserRole.values()) {
            User actor = users.saveAndFlush(new User("availability-" + role.name().toLowerCase() + "+"
                    + UUID.randomUUID() + "@example.test", "{argon2id}fixture-only", role));
            actors.put(role, actor);
            tokens.put(role, tokenService.issue(actor, permissionResolver.resolve(role)).accessToken());
        }
        veterinarian = actors.get(UserRole.VETERINARIAN);
        var now = clock.instant();
        var profile = veterinarianProfiles.saveAndFlush(new VeterinarianProfile(veterinarian,
                "Dra. Laura Marcela Ramírez", "+573001234567", "MV-" + veterinarian.getId().toString().substring(0, 12),
                "Perfil ficticio para pruebas de disponibilidad.", actors.get(UserRole.ADMINISTRATOR).getId(), now));
        qualifications.saveAndFlush(new VeterinarianQualification(profile, QualificationType.UNDERGRADUATE,
                "Medicina veterinaria", "Universidad de Ejemplo", 2020, true, now));
        appointmentDate = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).plusDays(2);
    }

    @Test
    void createsAndListsThirtyMinuteSlotsAndKeepsScheduleAccessScopedToTheVeterinarian() throws Exception {
        MvcResult created = createSlot(appointmentDate, "09:00", UserRole.ADMINISTRATOR, 201);
        JsonNode slot = body(created);
        String slotId = slot.path("id").asText();
        assertThat(slot.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(slot.path("timeZone").asText()).isEqualTo("America/Bogota");
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(BASE + "/" + veterinarian.getId() + "/availability-slots/" + slotId);
        assertThat(created.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store", "private");
        saveExample("availability-slot-created.json", created);

        MvcResult page = mvc.perform(authenticated(get(schedulePath()).param("from", appointmentDate.toString())
                        .param("to", appointmentDate.toString()), UserRole.ADMINISTRATOR))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(page).path("items").size()).isEqualTo(1);
        saveExample("availability-slot-page.json", page);

        mvc.perform(authenticated(get(schedulePath()), UserRole.VETERINARIAN))
                .andExpect(status().isBadRequest());
        mvc.perform(authenticated(get(BASE + "/" + UUID.randomUUID() + "/availability-slots")
                        .param("from", appointmentDate.toString()).param("to", appointmentDate.toString()), UserRole.VETERINARIAN))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("AVAILABILITY_SLOT_NOT_FOUND"));
        mvc.perform(authenticated(get(BASE + "/" + veterinarian.getId() + "/availability-slots/" + slotId), UserRole.VETERINARIAN))
                .andExpect(status().isOk());
        mvc.perform(authenticated(get(BASE + "/" + veterinarian.getId() + "/availability-slots/" + slotId + "/events"), UserRole.VETERINARIAN))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerSeesOnlyPublishedSlotsForActiveVeterinariansAndAccountReactivationRestoresThem() throws Exception {
        createSlot(appointmentDate, "09:00", UserRole.ADMINISTRATOR, 201);
        MvcResult visible = mvc.perform(authenticated(get(OWNER_BASE).param("from", appointmentDate.toString())
                        .param("to", appointmentDate.toString()), UserRole.OWNER))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andReturn();
        JsonNode ownerPage = body(visible);
        assertThat(ownerPage.path("items").size()).isEqualTo(1);
        JsonNode ownerSlot = ownerPage.path("items").get(0);
        assertThat(ownerSlot.path("veterinarianFullName").asText()).isEqualTo("Dra. Laura Marcela Ramírez");
        assertThat(ownerSlot.propertyNames()).doesNotContain("reason", "actorId", "email", "createdBy", "updatedBy", "status");
        saveExample("available-slot-page.json", visible);

        mvc.perform(authenticated(patch(BASE + "/" + veterinarian.getId() + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "DISABLED"))).andExpect(status().isOk());
        MvcResult hidden = mvc.perform(authenticated(get(OWNER_BASE).param("from", appointmentDate.toString())
                        .param("to", appointmentDate.toString()), UserRole.OWNER)).andExpect(status().isOk()).andReturn();
        assertThat(body(hidden).path("items")).isEmpty();

        mvc.perform(authenticated(patch(BASE + "/" + veterinarian.getId() + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "ACTIVE"))).andExpect(status().isOk());
        MvcResult restored = mvc.perform(authenticated(get(OWNER_BASE).param("from", appointmentDate.toString())
                        .param("to", appointmentDate.toString()), UserRole.OWNER)).andExpect(status().isOk()).andReturn();
        assertThat(body(restored).path("items").size()).isEqualTo(1);
    }

    @Test
    void blockAndRepublishKeepTheSlotIdentityAndRecordOnlyEffectiveChanges() throws Exception {
        JsonNode created = body(createSlot(appointmentDate, "09:00", UserRole.ADMINISTRATOR, 201));
        String path = schedulePath() + "/" + created.path("id").asText();
        MvcResult blocked = mvc.perform(authenticated(patch(path + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "BLOCKED", "expectedVersion", 0, "reason", "Ajuste ficticio de agenda")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn();
        assertThat(body(blocked).path("id").asText()).isEqualTo(created.path("id").asText());
        mvc.perform(authenticated(patch(path + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "BLOCKED", "expectedVersion", 1, "reason", "Sin cambios")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(authenticated(patch(path + "/status"), UserRole.ADMINISTRATOR,
                        Map.of("status", "PUBLISHED", "expectedVersion", 0, "reason", "Versión anterior")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        MvcResult eventPage = mvc.perform(authenticated(get(path + "/events"), UserRole.ADMINISTRATOR))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(eventPage).path("items").size()).isEqualTo(2);
        assertThat(body(eventPage).path("items").get(1).path("reason").asText()).isEqualTo("Ajuste ficticio de agenda");
        saveExample("availability-event-page.json", eventPage);
        mvc.perform(authenticated(get(path + "/events"), UserRole.OWNER)).andExpect(status().isForbidden());
    }

    @Test
    void rejectsNonAlignedTimesExtraBodyPropertiesAndInvalidDateRangesWithoutLeakingInternals() throws Exception {
        MvcResult invalid = mvc.perform(authenticated(post(schedulePath()), UserRole.ADMINISTRATOR,
                        Map.of("startsAt", appointmentDate + "T09:15:00-05:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY_REQUEST"))
                .andReturn();
        saveExample("availability-invalid-error.json", invalid);
        mvc.perform(authenticated(post(schedulePath()), UserRole.ADMINISTRATOR,
                        Map.of("startsAt", appointmentDate + "T09:00:00-05:00", "endsAt", "PRIVATE_REJECTED_INPUT")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("UNKNOWN_PROPERTY"));
        mvc.perform(authenticated(get(schedulePath()).param("from", appointmentDate.toString())
                        .param("to", appointmentDate.plusDays(31).toString()), UserRole.ADMINISTRATOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY_REQUEST"));
        mvc.perform(authenticated(post(schedulePath()), UserRole.OWNER,
                        Map.of("startsAt", appointmentDate + "T09:00:00-05:00")))
                .andExpect(status().isForbidden());
        mvc.perform(post(schedulePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("startsAt", appointmentDate + "T09:00:00-05:00"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchCreationIsBoundedInclusiveAndAtomicWhenAnyRequestedStartConflicts() throws Exception {
        createSlot(appointmentDate, "09:00", UserRole.ADMINISTRATOR, 201);
        int isoDay = appointmentDate.getDayOfWeek().getValue();
        MvcResult conflict = mvc.perform(authenticated(post(batchPath()), UserRole.ADMINISTRATOR,
                        Map.of("startDate", appointmentDate.toString(), "endDate", appointmentDate.toString(),
                                "daysOfWeek", List.of(isoDay), "dailyStartTime", "09:00", "dailyEndTime", "10:00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AVAILABILITY_SLOT_CONFLICT"))
                .andReturn();
        saveExample("availability-conflict-error.json", conflict);
    }

    @Test
    void ownerWindowExcludesTodayAndDatesBeyondSixtyDaysButIncludesTomorrow() throws Exception {
        LocalDateTime currentLocal = LocalDateTime.now(clock.withZone(ZoneId.of("America/Bogota")));
        int minutesUntilNextSlot = 30 - currentLocal.getMinute() % 30;
        LocalDateTime nearStart = currentLocal.withSecond(0).withNano(0).plusMinutes(minutesUntilNextSlot);
        createSlotAt(nearStart, UserRole.ADMINISTRATOR, 201);
        LocalDate farDate = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).plusDays(61);
        createSlot(farDate, "09:00", UserRole.ADMINISTRATOR, 201);
        LocalDate tomorrow = LocalDate.now(clock.withZone(ZoneId.of("America/Bogota"))).plusDays(1);
        createSlot(tomorrow, "23:30", UserRole.ADMINISTRATOR, 201);

        MvcResult nearPage = mvc.perform(authenticated(get(OWNER_BASE).param("from", nearStart.toLocalDate().toString())
                        .param("to", nearStart.toLocalDate().toString()), UserRole.OWNER))
                .andExpect(status().isOk()).andReturn();
        MvcResult farPage = mvc.perform(authenticated(get(OWNER_BASE).param("from", farDate.toString())
                        .param("to", farDate.toString()), UserRole.OWNER))
                .andExpect(status().isOk()).andReturn();
        MvcResult tomorrowPage = mvc.perform(authenticated(get(OWNER_BASE).param("from", tomorrow.toString())
                        .param("to", tomorrow.toString()), UserRole.OWNER))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(nearPage).path("items")).isEmpty();
        assertThat(body(farPage).path("items")).isEmpty();
        assertThat(body(tomorrowPage).path("items").size()).isEqualTo(1);
    }

    @Test
    void batchAcceptsTheExactThousandSlotLimitAndSupportsExclusiveMidnight() throws Exception {
        LocalDate startDate = appointmentDate.plusDays(1);
        List<Integer> allDays = List.of(1, 2, 3, 4, 5, 6, 7);
        MvcResult overnight = mvc.perform(authenticated(post(batchPath()), UserRole.ADMINISTRATOR,
                        Map.of("startDate", startDate.toString(), "endDate", startDate.toString(),
                                "daysOfWeek", List.of(startDate.getDayOfWeek().getValue()),
                                "dailyStartTime", "23:30", "dailyEndTime", "24:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdCount").value(1)).andReturn();
        JsonNode overnightSlot = body(overnight).path("items").get(0);
        assertThat(overnightSlot.path("endsAt").asText()).isEqualTo(startDate.plusDays(1) + "T05:00:00Z");
        saveExample("availability-slot-batch-created.json", overnight);

        LocalDate rangeStart = appointmentDate.plusDays(3);
        Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int day = 0; day < 29; day++) {
            counts.merge(rangeStart.plusDays(day).getDayOfWeek().getValue(), 1, Integer::sum);
        }
        int excludedFourDayWeekday = counts.entrySet().stream().filter(entry -> entry.getValue() == 4)
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        List<Integer> selectedDays = allDays.stream().filter(day -> day != excludedFourDayWeekday).toList();
        MvcResult exactLimit = mvc.perform(authenticated(post(batchPath()), UserRole.ADMINISTRATOR,
                        Map.of("startDate", rangeStart.toString(), "endDate", rangeStart.plusDays(28).toString(),
                                "daysOfWeek", selectedDays, "dailyStartTime", "00:00", "dailyEndTime", "20:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdCount").value(1000)).andReturn();
        assertThat(body(exactLimit).path("items").size()).isEqualTo(1000);
    }

    @Test
    void batchRejectsMoreThanOneThousandGeneratedSlotsBeforeInsertingAny() throws Exception {
        LocalDate rangeStart = appointmentDate.plusDays(3);
        mvc.perform(authenticated(post(batchPath()), UserRole.ADMINISTRATOR,
                        Map.of("startDate", rangeStart.toString(), "endDate", rangeStart.plusDays(30).toString(),
                                "daysOfWeek", List.of(1, 2, 3, 4, 5, 6, 7),
                                "dailyStartTime", "00:00", "dailyEndTime", "16:30")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_AVAILABILITY_REQUEST"));
    }

    @Test
    void generatesTheAvailabilityContractWithExpectedRoutesSecurityAndPrivacyBoundaries() throws Exception {
        JsonNode specification = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        JsonNode paths = specification.path("paths");
        assertThat(paths.propertyNames()).contains(BASE + "/{veterinarianId}/availability-slots",
                BASE + "/{veterinarianId}/availability-slot-batches", OWNER_BASE);
        assertThat(paths.path(OWNER_BASE).path("get").path("security").isArray()).isTrue();
        assertThat(paths.path(BASE + "/{veterinarianId}/availability-slots").path("get").path("responses")
                .propertyNames()).contains("200", "400", "401", "403", "404", "409");
        assertThat(paths.path(BASE + "/{veterinarianId}/availability-slots").path("post").path("responses")
                .propertyNames()).contains("201", "400", "401", "403", "404", "409");
        JsonNode schemas = specification.path("components").path("schemas");
        Map<String, List<String>> requiredResponseFields = Map.of(
                "VeterinarianAvailabilitySlotResponseDTO", List.of("id", "veterinarianId", "startsAt", "endsAt", "status", "version", "timeZone"),
                "AvailableVeterinarianSlotResponseDTO", List.of("id", "veterinarianId", "veterinarianFullName", "startsAt", "endsAt", "version", "timeZone"),
                "VeterinarianAvailabilityEventResponseDTO", List.of("id", "slotId", "slotVersion", "actorId", "occurredAt", "eventType",
                        "previousStartsAt", "previousEndsAt", "previousStatus", "newStartsAt", "newEndsAt", "newStatus", "reason"),
                "VeterinarianAvailabilitySlotPageResponseDTO", List.of("items", "page", "size", "totalElements", "totalPages"),
                "AvailableVeterinarianSlotPageResponseDTO", List.of("items", "page", "size", "totalElements", "totalPages"),
                "VeterinarianAvailabilityEventPageResponseDTO", List.of("items", "page", "size", "totalElements", "totalPages"),
                "CreateVeterinarianAvailabilitySlotBatchResponseDTO", List.of("createdCount", "items", "timeZone"));
        requiredResponseFields.forEach((name, fields) -> {
            JsonNode schema = schemas.path(name);
            assertThat(schema.path("required").valueStream().map(JsonNode::asText).toList())
                    .as("Required response fields of %s", name).containsExactlyInAnyOrderElementsOf(fields);
            assertThat(schema.path("properties").propertyNames()).containsExactlyInAnyOrderElementsOf(fields);
            assertThat(schema.path("additionalProperties").isBoolean()).isTrue();
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            for (String field : fields) {
                if (List.of("version", "slotVersion", "page", "size", "totalElements", "totalPages", "createdCount").contains(field)) {
                    assertThat(schema.path("properties").path(field).path("type").asText())
                            .as("JSON numeric type of %s.%s", name, field).isEqualTo("integer");
                }
            }
        });
        for (String name : List.of("VeterinarianAvailabilitySlotPageResponseDTO", "AvailableVeterinarianSlotPageResponseDTO",
                "VeterinarianAvailabilityEventPageResponseDTO")) {
            JsonNode properties = schemas.path(name).path("properties");
            assertThat(properties.path("items").path("maxItems").asInt()).isEqualTo(100);
            assertThat(properties.path("size").path("minimum").asInt()).isEqualTo(1);
            assertThat(properties.path("size").path("maximum").asInt()).isEqualTo(100);
            assertThat(properties.path("totalElements").path("format").asText()).isEqualTo("int64");
        }
        JsonNode batchProperties = schemas.path("CreateVeterinarianAvailabilitySlotBatchResponseDTO").path("properties");
        assertThat(batchProperties.path("createdCount").path("minimum").asInt()).isEqualTo(1);
        assertThat(batchProperties.path("createdCount").path("maximum").asInt()).isEqualTo(1000);
        assertThat(batchProperties.path("items").path("minItems").asInt()).isEqualTo(1);
        assertThat(batchProperties.path("items").path("maxItems").asInt()).isEqualTo(1000);
        assertThat(batchProperties.path("timeZone").path("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly("America/Bogota");
        assertThat(schemas.path("AvailableVeterinarianSlotResponseDTO").path("properties").propertyNames())
                .containsExactlyInAnyOrder("id", "veterinarianId", "veterinarianFullName", "startsAt", "endsAt", "version", "timeZone")
                .doesNotContain("email", "reason", "actorId", "status");
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").propertyNames())
                .contains("previousStartsAt", "previousEndsAt", "previousStatus", "reason", "actorId");
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path("previousStatus").path("type")
                .valueStream().map(JsonNode::asText).toList()).contains("string", "null");
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path("previousStatus").path("enum")
                .valueStream().map(node -> node.isNull() ? "null" : node.asText()).toList())
                .containsExactlyInAnyOrder("PUBLISHED", "BLOCKED", "null");
        assertThat(schemas.path("VeterinarianAvailabilitySlotResponseDTO").path("properties").path("status").path("enum")
                .valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder("PUBLISHED", "BLOCKED");
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path("newStatus").path("enum")
                .valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder("PUBLISHED", "BLOCKED");
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path("eventType").path("enum")
                .valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder("CREATED", "RESCHEDULED", "BLOCKED", "PUBLISHED");
        for (String nullableField : List.of("previousStartsAt", "previousEndsAt", "previousStatus", "reason")) {
            assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path(nullableField).path("type")
                    .valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder("string", "null");
        }
        assertThat(schemas.path("VeterinarianAvailabilityEventResponseDTO").path("properties").path("reason").path("minLength").asInt())
                .isEqualTo(1);
        assertThat(schemas.path("CreateVeterinarianAvailabilitySlotBatchRequestDTO").path("properties")
                .path("daysOfWeek").path("minItems").asInt()).isEqualTo(1);
        assertThat(specification.path("info").path("version").asText()).isEqualTo("0.7.0");
        Path output = Path.of("target", "generated-openapi");
        Files.createDirectories(output);
        Files.writeString(output.resolve("openapi.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(specification));
    }

    private MvcResult createSlot(LocalDate date, String time, UserRole role, int status) throws Exception {
        return mvc.perform(authenticated(post(schedulePath()), role,
                        Map.of("startsAt", date + "T" + time + ":00-05:00")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status)).andReturn();
    }

    private MvcResult createSlotAt(LocalDateTime start, UserRole role, int expectedStatus) throws Exception {
        String value = start.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime().toString();
        return mvc.perform(authenticated(post(schedulePath()), role, Map.of("startsAt", value)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expectedStatus)).andReturn();
    }

    private String schedulePath() { return BASE + "/" + veterinarian.getId() + "/availability-slots"; }
    private String batchPath() { return BASE + "/" + veterinarian.getId() + "/availability-slot-batches"; }
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UserRole role) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role));
    }
    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, UserRole role, Object body) throws Exception {
        return authenticated(request, role).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }
    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }
}
