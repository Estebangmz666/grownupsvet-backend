package edu.uniquindio.grownupsvet.grownupsvet_backend.pet;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.pet.controller.PetController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.OwnerProfile;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.OwnerProfileRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL, bearer tokens and committed transactions exercise ownership and partial updates. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class PetHttpIntegrationTests {
    private static final String PETS_PATH = PetController.PETS_PATH;
    private static final String RAW_PASSWORD = "Una frase privada para mis mascotas";
    private static final String MINIMAL_PET = "{\"name\":\"Luna\",\"species\":\"DOG\"}";
    private static final String COMPLETE_PET = """
            {"name":" Luna ","species":"DOG","breed":" Mestiza ","sex":"FEMALE",
             "dateOfBirth":"2020-05-15","dateOfBirthEstimated":true}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerProfileRepository ownerProfileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    private final List<UUID> accountIds = new ArrayList<>();
    private User owner;
    private String token;

    @BeforeEach
    void createOwner() throws Exception {
        owner = createAccount(UserRole.OWNER);
        token = login(owner);
    }

    @AfterEach
    void removeOnlyThisTestsAccounts() {
        for (UUID accountId : accountIds) {
            jdbcTemplate.update("DELETE FROM pets WHERE owner_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM owner_profiles WHERE user_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", accountId);
        }
    }

    @Test
    void oneOwnerCreatesSeveralPetsAndServerAssignsOwnershipAndActiveState() throws Exception {
        MvcResult result = createPet(token, COMPLETE_PET)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.name").value("Luna"))
                .andExpect(jsonPath("$.species").value("DOG"))
                .andExpect(jsonPath("$.breed").value("Mestiza"))
                .andExpect(jsonPath("$.sex").value("FEMALE"))
                .andExpect(jsonPath("$.dateOfBirth").value("2020-05-15"))
                .andExpect(jsonPath("$.dateOfBirthEstimated").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andReturn();
        JsonNode first = response(result);
        UUID firstPetId = UUID.fromString(first.path("id").asText());
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(PETS_PATH + "/" + firstPetId);
        assertThat(first.path("createdAt").asText()).isEqualTo(first.path("updatedAt").asText());
        saveExample("pet-created.json", result);

        JsonNode second = response(createPet(token, "{\"name\":\"Milo\",\"species\":\"CAT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.breed").isEmpty())
                .andExpect(jsonPath("$.sex").isEmpty())
                .andExpect(jsonPath("$.dateOfBirth").isEmpty())
                .andExpect(jsonPath("$.dateOfBirthEstimated").value(false))
                .andReturn());
        assertThat(second.path("id").asText()).isNotEqualTo(firstPetId.toString());
        assertThat(jdbcTemplate.queryForList("SELECT owner_id FROM pets WHERE owner_id = ?", UUID.class,
                owner.getId())).containsExactly(owner.getId(), owner.getId());
    }

    @Test
    void anotherOwnersPetsStayHiddenAcrossListGetAndPatch() throws Exception {
        String ownPetId = createPetId(COMPLETE_PET);
        User anotherOwner = createAccount(UserRole.OWNER);
        String anotherToken = login(anotherOwner);
        String otherPetId = response(createPet(anotherToken, "{\"name\":\"Nube\",\"species\":\"CAT\"}")
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        listPets(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ownPetId));
        listPets(anotherToken, "?active=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(otherPetId));

        JsonNode foreignError = response(getPet(anotherToken, ownPetId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PET_NOT_FOUND"))
                .andReturn());
        JsonNode absentError = response(getPet(anotherToken, UUID.randomUUID().toString())
                .andExpect(status().isNotFound()).andReturn());
        assertThat(foreignError.path("detail")).isEqualTo(absentError.path("detail"));
        assertThat(foreignError.path("errorCode")).isEqualTo(absentError.path("errorCode"));
        for (String petId : List.of(ownPetId, UUID.randomUUID().toString())) {
            updatePet(anotherToken, petId, "{\"name\":\"Otro nombre\",\"active\":false}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PET_NOT_FOUND"));
        }
        getPet(token, ownPetId).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luna"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void partialUpdatePreservesOmittedFieldsAndNullClearsOnlyOptionalFields() throws Exception {
        String petId = createPetId(COMPLETE_PET);
        updatePet(token, petId, "{\"name\":\"  Luna María  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luna María"))
                .andExpect(jsonPath("$.species").value("DOG"))
                .andExpect(jsonPath("$.breed").value("Mestiza"))
                .andExpect(jsonPath("$.sex").value("FEMALE"))
                .andExpect(jsonPath("$.dateOfBirth").value("2020-05-15"))
                .andExpect(jsonPath("$.dateOfBirthEstimated").value(true));

        MvcResult invalid = updatePet(token, petId, "{\"name\":\"No se guarda\",\"dateOfBirth\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("dateOfBirthEstimated"))
                .andReturn();
        saveExample("pet-invalid.json", invalid);
        getPet(token, petId).andExpect(jsonPath("$.name").value("Luna María"))
                .andExpect(jsonPath("$.dateOfBirth").value("2020-05-15"));

        updatePet(token, petId,
                "{\"breed\":null,\"sex\":null,\"dateOfBirth\":null,\"dateOfBirthEstimated\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luna María"))
                .andExpect(jsonPath("$.breed").isEmpty())
                .andExpect(jsonPath("$.sex").isEmpty())
                .andExpect(jsonPath("$.dateOfBirth").isEmpty())
                .andExpect(jsonPath("$.dateOfBirthEstimated").value(false));
        updatePet(token, petId, "{\"sex\":\"UNKNOWN\",\"species\":\"CAT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sex").value("UNKNOWN"))
                .andExpect(jsonPath("$.species").value("CAT"));

        JsonNode beforeEmptyPatch = response(getPet(token, petId).andReturn());
        JsonNode afterEmptyPatch = response(updatePet(token, petId, "{}").andExpect(status().isOk()).andReturn());
        assertThat(afterEmptyPatch).isEqualTo(beforeEmptyPatch);
    }

    @Test
    void archiveAndReactivatePreserveIdentityAndCreationDataWithExplicitListFilters() throws Exception {
        String petId = createPetId(COMPLETE_PET);
        JsonNode original = response(getPet(token, petId).andReturn());
        updatePet(token, petId, "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(petId))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.dateOfBirth").value("2020-05-15"))
                .andExpect(jsonPath("$.createdAt").value(original.path("createdAt").asText()));
        getPet(token, petId).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        listPets(token, "").andExpect(jsonPath("$.totalElements").value(1));
        listPets(token, "?active=true").andExpect(jsonPath("$.totalElements").value(0));
        listPets(token, "?active=false").andExpect(jsonPath("$.items[0].id").value(petId));
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM pets WHERE id = ?", UUID.class,
                UUID.fromString(petId))).isEqualTo(owner.getId());
        updatePet(token, petId, "{\"active\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(petId)).andExpect(jsonPath("$.active").value(true));
        listPets(token, "?active=false").andExpect(jsonPath("$.totalElements").value(0));
        listPets(token, "?active=true").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void paginatesWithBoundedSizesAndStableOrder() throws Exception {
        List<String> petIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            petIds.add(createPetId("{\"name\":\"Mascota " + index + "\",\"species\":\"DOG\"}"));
        }
        listPets(token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(1));
        MvcResult firstPage = listPets(token, "?page=0&size=2").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(petIds.get(2)))
                .andExpect(jsonPath("$.items[1].id").value(petIds.get(1)))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andReturn();
        saveExample("pet-page.json", firstPage);
        listPets(token, "?page=1&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(petIds.getFirst()));
        listPets(token, "?page=5&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalElements").value(3));
        listPets(token, "?size=100").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void allOperationsRequireABearerToken() throws Exception {
        String petId = createPetId(MINIMAL_PET);
        mockMvc.perform(post(PETS_PATH).contentType(MediaType.APPLICATION_JSON).content(MINIMAL_PET))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PETS_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PETS_PATH + "/" + petId)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(PETS_PATH + "/" + petId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"VETERINARIAN", "ADMINISTRATOR"})
    void staffCannotCreateListReadOrChangePets(UserRole role) throws Exception {
        String petId = createPetId(MINIMAL_PET);
        String staffToken = login(createAccount(role));
        createPet(staffToken, MINIMAL_PET).andExpect(status().isForbidden());
        listPets(staffToken, "").andExpect(status().isForbidden());
        getPet(staffToken, petId).andExpect(status().isForbidden());
        updatePet(staffToken, petId, "{\"active\":false}").andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("invalidCreateRequests")
    void invalidCreateRequestsCannotPersistAnyPet(String json) throws Exception {
        createPet(token, json).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pets WHERE owner_id = ?", Long.class,
                owner.getId())).isZero();
    }

    static Stream<String> invalidCreateRequests() {
        return Stream.of(
                "{}", "{\"name\":\"Luna\"}", "{\"name\":\" \",\"species\":\"DOG\"}",
                "{\"name\":123,\"species\":\"DOG\"}", "{\"name\":\"Luna\",\"species\":0}",
                "{\"name\":\"Luna\",\"species\":\"RABBIT\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"sex\":1}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirthEstimated\":true}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirthEstimated\":null}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirthEstimated\":\"false\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirthEstimated\":0}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"breed\":\"   \"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"active\":false}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"ownerId\":\"" + UUID.randomUUID() + "\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirth\":\"0000-01-01\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirth\":\"2020-02-30\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirth\":[2020,1,1]}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"dateOfBirth\":20200101}",
                "{\"name\":\"Lu\\u0000na\",\"species\":\"DOG\"}",
                "{\"name\":\"Luna\",\"species\":\"DOG\",\"breed\":\"Mes\\u0000tiza\"}",
                "{\"name\":\"" + "a".repeat(101) + "\",\"species\":\"DOG\"}");
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateRequests")
    void invalidPartialUpdatesCannotChangeExistingPet(String json) throws Exception {
        String petId = createPetId(COMPLETE_PET);
        JsonNode original = response(getPet(token, petId).andReturn());
        updatePet(token, petId, json).andExpect(status().isBadRequest());
        assertThat(response(getPet(token, petId).andReturn())).isEqualTo(original);
    }

    static Stream<String> invalidUpdateRequests() {
        return Stream.of("{\"name\":null}", "{\"species\":null}", "{\"active\":null}",
                "{\"dateOfBirthEstimated\":null}", "{\"active\":\"false\"}", "{\"active\":0}",
                "{\"active\":0.1}", "{\"species\":0}", "{\"species\":\"0\"}", "{\"name\":\"  \"}",
                "{\"dateOfBirth\":null}", "{\"ownerId\":\"" + UUID.randomUUID() + "\"}",
                "{\"id\":\"" + UUID.randomUUID() + "\"}", "{\"suppliedFields\":[\"ACTIVE\"]}",
                "{\"name\":\"Lu\\u0000na\"}", "{\"breed\":\"Mes\\ntiza\"}",
                "{\"namePresent\":true}", "{\"breed\":\"" + "b".repeat(101) + "\"}");
    }

    @Test
    void textLimitsCountUnicodeCodePointsWithoutRejectingSupplementaryCharacters() throws Exception {
        String maximumLengthText = "🐕".repeat(100);
        String petId = createPetId(jsonMapper.writeValueAsString(Map.of("name", maximumLengthText,
                "species", "DOG", "breed", maximumLengthText)));
        getPet(token, petId).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(maximumLengthText))
                .andExpect(jsonPath("$.breed").value(maximumLengthText));
        updatePet(token, petId, jsonMapper.writeValueAsString(Map.of("name", maximumLengthText + "🐕")))
                .andExpect(status().isBadRequest());
        updatePet(token, petId, jsonMapper.writeValueAsString(Map.of("breed", maximumLengthText + "🐕")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void datesUseTheApplicationClockAndMayBeExactEstimatedOrUnknown() throws Exception {
        String today = LocalDate.now(clock).toString();
        String tomorrow = LocalDate.now(clock).plusDays(1).toString();
        createPet(token, "{\"name\":\"Hoy\",\"species\":\"CAT\",\"dateOfBirth\":\"" + today + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dateOfBirthEstimated").value(false));
        String petId = createPetId(MINIMAL_PET);
        createPet(token, "{\"name\":\"Mañana\",\"species\":\"CAT\",\"dateOfBirth\":\"" + tomorrow + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("dateOfBirth"));
        updatePet(token, petId, "{\"dateOfBirth\":\"" + tomorrow + "\"}").andExpect(status().isBadRequest());
        updatePet(token, petId, "{\"dateOfBirth\":\"2021-01-01\",\"dateOfBirthEstimated\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateOfBirthEstimated").value(true));
        updatePet(token, petId, "{\"dateOfBirthEstimated\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateOfBirth").value("2021-01-01"));
    }

    @Test
    void malformedIdentifiersAndOutOfRangePaginationReturnClientErrors() throws Exception {
        getPet(token, "not-a-uuid").andExpect(status().isBadRequest());
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?size=1.5", "?active=perhaps",
                "?page=2147483647&size=100")) {
            listPets(token, query).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void databaseRequiresExactlyOneExistingOwnerProfileForEachPet() throws Exception {
        UUID petId = UUID.fromString(createPetId(MINIMAL_PET));
        UUID veterinarianId = createAccount(UserRole.VETERINARIAN).getId();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE pets SET owner_id = NULL WHERE id = ?", petId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE pets SET owner_id = ? WHERE id = ?", veterinarianId, petId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM pets WHERE id = ?", UUID.class, petId))
                .isEqualTo(owner.getId());
    }

    @Test
    void concurrentPartialUpdatesRetainBothIndependentChanges() throws Exception {
        String petId = createPetId(MINIMAL_PET);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 4; index++) {
                String name = "Nombre " + index;
                String breed = "Raza " + index;
                CyclicBarrier start = new CyclicBarrier(2);
                var nameChange = executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    updatePet(token, petId, jsonMapper.writeValueAsString(Map.of("name", name)))
                            .andExpect(status().isOk());
                    return null;
                });
                var breedChange = executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    updatePet(token, petId, jsonMapper.writeValueAsString(Map.of("breed", breed)))
                            .andExpect(status().isOk());
                    return null;
                });
                nameChange.get(20, TimeUnit.SECONDS);
                breedChange.get(20, TimeUnit.SECONDS);
                getPet(token, petId).andExpect(jsonPath("$.name").value(name)).andExpect(jsonPath("$.breed").value(breed));
            }
        }
    }

    @Test
    void generatedOpenApiDefinesExactPetOperationsTypesNullabilityAndAllowedFields() throws Exception {
        JsonNode specification = response(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        JsonNode collection = specification.path("paths").path(PETS_PATH);
        assertThat(collection.propertyNames()).containsExactlyInAnyOrder("post", "get");
        assertThat(specification.path("paths").path(PETS_PATH + "/{petId}").propertyNames())
                .containsExactlyInAnyOrder("get", "patch");
        assertThat(collection.path("post").path("security").isArray()).isTrue();
        JsonNode schemas = specification.path("components").path("schemas");
        JsonNode creation = schemas.path("CreatePetRequestDTO");
        JsonNode update = schemas.path("UpdatePetRequestDTO");
        JsonNode pet = schemas.path("PetResponseDTO");
        assertThat(creation.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(update.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(creation.path("required").valueStream().map(JsonNode::asText).toList())
                .containsExactlyInAnyOrder("name", "species");
        assertThat(update.path("required").isMissingNode() || update.path("required").isEmpty()).isTrue();
        assertThat(creation.path("properties").propertyNames()).containsExactlyInAnyOrder(
                "name", "species", "breed", "sex", "dateOfBirth", "dateOfBirthEstimated");
        assertThat(update.path("properties").propertyNames()).containsExactlyInAnyOrder(
                "name", "species", "breed", "sex", "dateOfBirth", "dateOfBirthEstimated", "active");
        assertThat(creation.path("properties").path("species").path("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly("DOG", "CAT");
        for (JsonNode schema : List.of(creation, update, pet)) {
            assertThat(schema.path("properties").path("name").path("type").asText()).isEqualTo("string");
            assertThat(schema.path("properties").path("name").path("maxLength").asInt()).isEqualTo(100);
            assertThat(schema.path("properties").path("dateOfBirthEstimated").path("type").asText()).isEqualTo("boolean");
            for (String nullableField : List.of("breed", "sex", "dateOfBirth")) {
                assertThat(schema.path("properties").path(nullableField).path("type")
                        .valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder("string", "null");
            }
            assertThat(schema.path("properties").path("sex").path("enum").valueStream().anyMatch(JsonNode::isNull)).isTrue();
        }
        assertThat(pet.path("required").valueStream().map(JsonNode::asText).toList()).containsExactlyInAnyOrder(
                "id", "name", "species", "breed", "sex", "dateOfBirth", "dateOfBirthEstimated", "active", "createdAt", "updatedAt");
        assertThat(update.path("properties").path("active").path("type").asText()).isEqualTo("boolean");
        assertThat(schemas.path("PetPageResponseDTO").path("properties").path("totalElements").path("format").asText())
                .isEqualTo("int64");
        for (String numericField : List.of("page", "size", "totalElements", "totalPages")) {
            assertThat(schemas.path("PetPageResponseDTO").path("properties").path(numericField).path("type").asText())
                    .isEqualTo("integer");
        }
    }

    private User createAccount(UserRole role) {
        User account = userRepository.saveAndFlush(new User("pet-test+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(RAW_PASSWORD), role));
        accountIds.add(account.getId());
        if (role == UserRole.OWNER) {
            ownerProfileRepository.saveAndFlush(new OwnerProfile(account, "María Gómez", LocalDate.of(1955, 5, 20),
                    "+573001234567"));
        }
        return account;
    }

    private String login(User account) throws Exception {
        return response(mockMvc.perform(post(UserSessionController.SESSIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", account.getEmail(), "password", RAW_PASSWORD))))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asText();
    }

    private String createPetId(String json) throws Exception {
        return response(createPet(token, json).andExpect(status().isCreated()).andReturn()).path("id").asText();
    }

    private ResultActions createPet(String accessToken, String json) throws Exception {
        return mockMvc.perform(post(PETS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updatePet(String accessToken, String petId, String json) throws Exception {
        return mockMvc.perform(patch(PETS_PATH + "/" + petId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getPet(String accessToken, String petId) throws Exception {
        return mockMvc.perform(get(PETS_PATH + "/" + petId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions listPets(String accessToken, String query) throws Exception {
        return mockMvc.perform(get(PETS_PATH + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private JsonNode response(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private void saveExample(String filename, MvcResult result) throws Exception {
        Path directory = Path.of("target", "generated-openapi", "examples");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(filename), result.getResponse().getContentAsString());
    }
}
