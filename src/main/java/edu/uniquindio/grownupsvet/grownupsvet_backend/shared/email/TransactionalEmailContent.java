package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.email;

import java.util.Objects;

/** Alternative representations of the same transactional email. */
public record TransactionalEmailContent(String subject, String plainText, String html) {
    public TransactionalEmailContent {
        Objects.requireNonNull(subject, "Email subject is required");
        Objects.requireNonNull(plainText, "Plain-text email content is required");
        Objects.requireNonNull(html, "HTML email content is required");
    }

    @Override
    public String toString() {
        return "TransactionalEmailContent[subject=[REDACTED], plainText=[REDACTED], html=[REDACTED]]";
    }
}
