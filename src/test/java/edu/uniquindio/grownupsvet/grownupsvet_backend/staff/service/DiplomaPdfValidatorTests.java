package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.assertj.core.api.Assertions.*;

class DiplomaPdfValidatorTests {
    private final DiplomaPdfValidator validator = new DiplomaPdfValidator();

    @Test
    void acceptsPassivePdfAndPreservesItsExactBytes() throws Exception {
        byte[] bytes = pdf(document -> { });
        assertThat(validator.validate(file(bytes))).isEqualTo(bytes);
        Files.write(Path.of("target", "pdf-validation-probe.pdf"), bytes);
    }

    @Test
    void highlyCompressedPdfCannotExhaustTheServerJvmAndNextValidationStillWorks() throws Exception {
        byte[] compressedBomb = pdfWithHighlyCompressedContent(512);
        assertThat(compressedBomb.length).isLessThan(DiplomaPdfValidator.MAX_BYTES);
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            assertInvalid(file(compressedBomb));
            byte[] validPdf = pdf(document -> { });
            assertThat(validator.validate(file(validPdf))).isEqualTo(validPdf);
        });
    }

    @Test
    void rejectsDisguisedAndTruncatedFiles() {
        assertInvalid(new MockMultipartFile("file", "diploma.pdf", "application/pdf", "<html>fake</html>".getBytes(StandardCharsets.UTF_8)));
        assertInvalid(file("%PDF-1.7\ntruncated".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsOversizeBeforeParsing() {
        assertThatThrownBy(() -> validator.validate(file(new byte[DiplomaPdfValidator.MAX_BYTES + 1])))
                .isInstanceOfSatisfying(StaffOperationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(exception.getErrorCode()).isEqualTo("DIPLOMA_TOO_LARGE");
                });
    }

    @Test
    void rejectsWrongMediaTypeEvenWhenBytesArePdf() throws Exception {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "diploma.pdf", "text/plain", pdf(document -> { }))))
                .isInstanceOfSatisfying(StaffOperationException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsJavascriptAndNestedPageActions() throws Exception {
        assertInvalid(file(pdf(document -> document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('test')")))));
        assertInvalid(file(pdf(document -> {
            COSDictionary action = new COSDictionary();
            action.setName(COSName.S, "URI"); action.setString(COSName.getPDFName("URI"), "https://example.com");
            COSDictionary annotation = new COSDictionary();
            annotation.setName(COSName.SUBTYPE, "Link"); annotation.setItem(COSName.A, action);
            COSArray annotations = new COSArray(); annotations.add(annotation);
            document.getPage(0).getCOSObject().setItem(COSName.ANNOTS, annotations);
        })));
    }

    @Test
    void rejectsEncryptedDocumentsEvenWithEmptyUserPassword() throws Exception {
        assertInvalid(file(pdf(document -> {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
        })));
    }

    @Test
    void rejectsEmbeddedFilesAndForms() throws Exception {
        assertInvalid(file(pdf(document -> {
            COSDictionary names = new COSDictionary();
            names.setItem(COSName.getPDFName("EmbeddedFiles"), new COSDictionary());
            document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
        })));
        assertInvalid(file(pdf(document -> document.getDocumentCatalog().getCOSObject()
                .setItem(COSName.ACRO_FORM, new COSDictionary()))));
    }

    @Test
    void rejectsTooManyPagesAndOversizedDecodedStreams() throws Exception {
        assertInvalid(file(pdf(document -> {
            for (int index = 1; index <= 50; index++) { document.addPage(new PDPage()); }
        })));
        assertInvalid(file(pdf(document -> {
            COSStream stream = document.getDocument().createCOSStream();
            try (var output = stream.createOutputStream(COSName.FLATE_DECODE)) {
                byte[] padding = new byte[1024 * 1024];
                for (int index = 0; index < 31; index++) { output.write(padding); }
            }
            document.getPage(0).getCOSObject().setItem(COSName.CONTENTS, stream);
        })));
    }

    private void assertInvalid(MockMultipartFile file) {
        assertThatThrownBy(() -> validator.validate(file)).isInstanceOfSatisfying(StaffOperationException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo("INVALID_DIPLOMA"));
    }

    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "diploma.pdf", "application/pdf", bytes); }
    private byte[] pdf(DocumentEditor editor) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); editor.edit(document); document.save(output); return output.toByteArray();
        }
    }
    @FunctionalInterface private interface DocumentEditor { void edit(PDDocument document) throws Exception; }

    /** Writes 512 MiB through a streaming compressor without allocating that amount in the test/server JVM. */
    private byte[] pdfWithHighlyCompressedContent(int expandedMebibytes) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            byte[] padding = new byte[1024 * 1024];
            for (int index = 0; index < expandedMebibytes; index++) { deflater.write(padding); }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        List<Integer> offsets = new ArrayList<>();
        for (String object : List.of("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R >>\nendobj\n")) {
            offsets.add(output.size());
            output.write(object.getBytes(StandardCharsets.US_ASCII));
        }
        offsets.add(output.size());
        output.write(("4 0 obj\n<< /Length " + compressed.size() + " /Filter /FlateDecode >>\nstream\n")
                .getBytes(StandardCharsets.US_ASCII));
        compressed.writeTo(output);
        output.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        int crossReferenceOffset = output.size();
        output.write("xref\n0 5\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) {
            output.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Root 1 0 R /Size 5 >>\nstartxref\n" + crossReferenceOffset + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }
}
