package edu.uniquindio.grownupsvet.grownupsvet_backend.user;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.controller.UserSessionController;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.repository.RevokedAccessTokenRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.support.TestJwtKeyConfiguration;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserProfilePhotoRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.service.OwnerProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtKeyConfiguration.class)
class UserProfilePhotoHttpIntegrationTests {
    private static final String RAW_PASSWORD = "Una frase privada para la fotografía";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfilePhotoRepository userProfilePhotoRepository;
    @Autowired private RevokedAccessTokenRepository revokedAccessTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private String token;

    @BeforeEach
    void createStaffAccount() throws Exception {
        user = userRepository.saveAndFlush(new User("photo+" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(RAW_PASSWORD), UserRole.VETERINARIAN));
        token = login();
    }

    @AfterEach
    void removeTestData() {
        revokedAccessTokenRepository.deleteForUser(user.getId());
        userProfilePhotoRepository.deleteById(user.getId());
        userRepository.deleteById(user.getId());
        userRepository.flush();
    }

    @Test
    void uploadsResizesReadsAndIdempotentlyDeletesAPrivatePng() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE,
                image("png", 700, 350));
        put(file).andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get(OwnerProfileService.PROFILE_PHOTO_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andReturn();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(256);

        mockMvc.perform(delete(OwnerProfileService.PROFILE_PHOTO_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isNoContent());
        mockMvc.perform(delete(OwnerProfileService.PROFILE_PHOTO_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer())).andExpect(status().isNoContent());
        mockMvc.perform(get(OwnerProfileService.PROFILE_PHOTO_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.errorCode").value("PROFILE_PHOTO_NOT_FOUND"));
    }

    @Test
    void rejectsOversizedUnsupportedAndInvalidImageContent() throws Exception {
        put(new MockMultipartFile("file", "large.png", MediaType.IMAGE_PNG_VALUE,
                new byte[2 * 1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("PROFILE_PHOTO_TOO_LARGE"));

        put(new MockMultipartFile("file", "avatar.gif", MediaType.IMAGE_GIF_VALUE, image("gif", 20, 20)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_PROFILE_PHOTO_TYPE"));

        put(new MockMultipartFile("file", "fake.png", MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PROFILE_PHOTO"));

        put(new MockMultipartFile("file", "wide.png", MediaType.IMAGE_PNG_VALUE, image("png", 8001, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PROFILE_PHOTO"));
    }

    @Test
    void generatedContractContainsTheMultipartFieldAndAllPhotoOperations() throws Exception {
        JsonNode specification = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode path = specification.path("paths").path(OwnerProfileService.PROFILE_PHOTO_PATH);
        assertThat(path.propertyNames()).contains("get", "put", "delete");
        JsonNode multipartSchema = path.path("put").path("requestBody").path("content")
                .path(MediaType.MULTIPART_FORM_DATA_VALUE).path("schema");
        assertThat(multipartSchema.path("required").valueStream().map(JsonNode::asText).toList())
                .contains("file");
        assertThat(multipartSchema.path("properties").path("file").path("format").asText())
                .isEqualTo("binary");
        assertThat(path.path("get").path("responses").propertyNames()).contains("200", "401", "404");
        assertThat(path.path("put").path("responses").propertyNames()).contains("204", "400", "401", "413", "415");
    }

    private org.springframework.test.web.servlet.ResultActions put(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart(OwnerProfileService.PROFILE_PHOTO_PATH).file(file)
                .with(request -> { request.setMethod("PUT"); return request; })
                .header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post(UserSessionController.SESSIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(), "password", RAW_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private String bearer() { return "Bearer " + token; }
}
