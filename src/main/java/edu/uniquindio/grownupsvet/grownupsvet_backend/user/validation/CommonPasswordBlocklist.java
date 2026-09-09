package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the versioned common-password list once at startup. Submitted passwords
 * never leave the application, and matching does not change the password used
 * by the password encoder. See the resource directory for provenance and license.
 */
@Component
public final class CommonPasswordBlocklist {

    private static final String RESOURCE_PATH = "/password-blocklist/seclists-10k-most-common.txt";

    private final Set<String> blockedPasswords;

    public CommonPasswordBlocklist() {
        try (InputStream inputStream = CommonPasswordBlocklist.class.getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("The configured common-password blocklist is missing.");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                blockedPasswords = reader.lines()
                        .filter(password -> !password.isEmpty())
                        .map(password -> password.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
            if (blockedPasswords.isEmpty()) {
                throw new IllegalStateException("The configured common-password blocklist is empty.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("The configured common-password blocklist could not be read.", exception);
        }
    }

    public boolean contains(String password) {
        return blockedPasswords.contains(password.toLowerCase(Locale.ROOT));
    }
}
