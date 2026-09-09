package edu.uniquindio.grownupsvet.grownupsvet_backend.user.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class UserTests {

    @Test
    void normalizesEmailOnCreationAndChangeWithoutAlteringThePasswordHash() {
        String passwordHash = "{bcrypt}$2a$12$testHashWithCaseAndSymbols";
        User user = new User("  Persona@Example.COM  ", passwordHash, UserRole.OWNER);

        assertThat(user.getEmail()).isEqualTo("persona@example.com");
        assertThat(user.getPasswordHash()).isEqualTo(passwordHash);

        user.changeEmail("  Otro@EXAMPLE.com  ");

        assertThat(user.getEmail()).isEqualTo("otro@example.com");
    }

    @Test
    void doesNotExposeThePasswordHashInJsonOrDiagnosticText() {
        String passwordHash = "{bcrypt}$2a$12$testHashWithCaseAndSymbols";
        User user = new User("persona@example.com", passwordHash, UserRole.OWNER);
        var mapper = new JacksonJsonHttpMessageConverter().getMapper();
        String json = mapper.writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash", "password_hash", passwordHash);
        assertThat(user.toString()).doesNotContain(passwordHash, "persona@example.com");
    }
}
