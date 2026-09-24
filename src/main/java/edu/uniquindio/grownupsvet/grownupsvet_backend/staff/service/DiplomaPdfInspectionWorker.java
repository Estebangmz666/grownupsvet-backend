package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Isolated PDF inspection entrypoint. Never starts Spring or reports parser diagnostics.
 * The launching process supplies the heap limit and deadline.
 */
public final class DiplomaPdfInspectionWorker {
    public static final int VALID_EXIT_CODE = 0;
    public static final int INVALID_EXIT_CODE = 2;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_PAGES = 50;
    private static final int MAX_OBJECTS = 10000;
    private static final int MAX_NODES = 50000;
    private static final long MAX_DECODED_BYTES = 30L * 1024 * 1024;
    private static final Set<String> FORBIDDEN_KEYS = Set.of("JS", "JavaScript", "AA", "OpenAction", "AcroForm",
            "XFA", "EmbeddedFiles", "EF", "RichMedia", "RichMediaContent", "RichMediaSettings", "Launch",
            "SubmitForm", "ImportData", "GoToR", "GoToE", "URI", "Rendition", "Sound", "Movie", "Collection", "AF");

    private DiplomaPdfInspectionWorker() { }

    public static void main(String[] arguments) {
        int result = INVALID_EXIT_CODE;
        try {
            if (arguments.length == 1) {
                Path input = Path.of(arguments[0]);
                if (Files.isRegularFile(input) && Files.size(input) > 0 && Files.size(input) <= MAX_BYTES) {
                    inspect(input);
                    result = VALID_EXIT_CODE;
                }
            }
        } catch (IOException | RuntimeException | LinkageError | StackOverflowError exception) {
            // File contents, parser diagnostics and paths never leave this worker.
            result = INVALID_EXIT_CODE;
        }
        System.exit(result);
    }

    private static void inspect(Path input) throws IOException {
        try (PDDocument document = Loader.loadPDF(input.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            if (document.isEncrypted()) { throw invalid(); }
            if (document.getDocument().getXrefTable().size() > MAX_OBJECTS) { throw invalid(); }
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) { throw invalid(); }
            inspectObjects(document);
        }
    }

    private static void inspectObjects(PDDocument document) throws IOException {
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(document.getDocument().getTrailer(), 0));
        for (COSObjectKey key : List.copyOf(document.getDocument().getXrefTable().keySet())) {
            pending.add(new Node(document.getDocument().getObjectFromPool(key), 0));
        }
        int nodeCount = 0;
        long decodedBytes = 0;
        byte[] buffer = new byte[8192];
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            COSBase value = node.value();
            if (value == null || !visited.add(value)) { continue; }
            if (++nodeCount > MAX_NODES || node.depth() > 64) { throw invalid(); }
            if (value instanceof COSObject object) {
                if (object.getObject() != null) { pending.addFirst(new Node(object.getObject(), node.depth() + 1)); }
            } else if (value instanceof COSDictionary dictionary) {
                if ("Action".equals(dictionary.getNameAsString(COSName.TYPE))
                        || "EmbeddedFile".equals(dictionary.getNameAsString(COSName.TYPE))
                        || dictionary.containsKey(COSName.getPDFName("F")) && dictionary instanceof COSStream) { throw invalid(); }
                for (COSName key : dictionary.keySet()) {
                    if (FORBIDDEN_KEYS.contains(key.getName()) || "A".equals(key.getName())) { throw invalid(); }
                    COSBase child = dictionary.getItem(key);
                    if (child != null) { pending.addLast(new Node(child, node.depth() + 1)); }
                }
                if (value instanceof COSStream stream) {
                    // PDFBox can allocate the complete decoded buffer before returning this stream.
                    // This is an acceptance limit; the separate JVM heap contains those allocations.
                    try (InputStream decoded = stream.createInputStream()) {
                        for (int read; (read = decoded.read(buffer)) != -1;) {
                            decodedBytes += read;
                            if (decodedBytes > MAX_DECODED_BYTES) { throw invalid(); }
                        }
                    }
                }
            } else if (value instanceof COSArray array) {
                for (COSBase child : array) {
                    if (child != null) { pending.addLast(new Node(child, node.depth() + 1)); }
                }
            }
            if (pending.size() > MAX_NODES) { throw invalid(); }
        }
    }

    private record Node(COSBase value, int depth) { }
    private static IOException invalid() { return new IOException("INVALID_DIPLOMA"); }
}
