package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception.StaffOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Validates file size in-process and isolates every PDFBox allocation in a bounded child JVM. */
@Component
public class DiplomaPdfValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int INSPECTION_TIMEOUT_SECONDS = 15;
    private static final String WORKER_CLASS = "edu.uniquindio.grownupsvet.grownupsvet_backend.staff.service.DiplomaPdfInspectionWorker";
    private static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String TEMPORARY_DIRECTORY_PREFIX = "grownupsvet-diploma-";
    private final Semaphore parsingSlots = new Semaphore(2);

    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) { throw invalid("Debes adjuntar un diploma PDF."); }
        if (file.getSize() > MAX_BYTES) { throw tooLarge(); }
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new StaffOperationException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_DIPLOMA_MEDIA_TYPE", "El diploma debe enviarse como application/pdf.");
        }
        if (!parsingSlots.tryAcquire()) {
            throw unavailable("DIPLOMA_VALIDATION_BUSY", "No se pudo validar el diploma en este momento. Intenta nuevamente.");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) { throw tooLarge(); }
            if (bytes.length < 12 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw invalid("El archivo no contiene un PDF válido.");
            }
            String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length), StandardCharsets.ISO_8859_1);
            if (!tail.stripTrailing().endsWith("%%EOF")) { throw invalid("El archivo PDF está incompleto o contiene datos adicionales."); }
            inspectInSeparateProcess(bytes);
            return bytes;
        } catch (StaffOperationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("DIPLOMA_VALIDATION_UNAVAILABLE", "No se pudo validar el diploma en este momento. Intenta nuevamente.");
        } finally {
            parsingSlots.release();
        }
    }

    private void inspectInSeparateProcess(byte[] bytes) throws IOException {
        Path directory = Files.createTempDirectory(TEMPORARY_DIRECTORY_PREFIX).toAbsolutePath().normalize();
        Process process = null;
        try {
            Path input = directory.resolve("diploma.pdf");
            Files.write(input, bytes);
            ProcessBuilder builder = new ProcessBuilder(workerCommand(directory, input));
            builder.directory(directory.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            // The worker does not need database, SMTP, JWT or application credentials.
            Map<String, String> environment = builder.environment();
            String systemRoot = environment.get("SystemRoot");
            environment.clear();
            if (systemRoot != null) { environment.put("SystemRoot", systemRoot); }
            environment.put("TMP", directory.toString());
            environment.put("TEMP", directory.toString());
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(INSPECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw invalid("El PDF excede el tiempo permitido de validación.");
            }
            if (process.exitValue() != 0) {
                throw invalid("No se pudo validar el PDF. Usa un documento sin cifrado, formularios, acciones ni adjuntos, dentro de los límites permitidos.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("DIPLOMA_VALIDATION_INTERRUPTED", "La validación del diploma fue interrumpida. Intenta nuevamente.");
        } finally {
            try { terminate(process); }
            finally { deleteTemporaryDirectory(directory); }
        }
    }

    private List<String> workerCommand(Path directory, Path input) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(javaExecutable.toString(), "-Xmx128m", "-Xss512k",
                "-XX:MaxDirectMemorySize=32m", "-XX:MaxMetaspaceSize=64m", "-XX:ActiveProcessorCount=1",
                "-XX:+ExitOnOutOfMemoryError", "-XX:-HeapDumpOnOutOfMemoryError",
                "-XX:-CreateCoredumpOnCrash", "-XX:ErrorFile=" + (windows ? "NUL" : "/dev/null"),
                "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + directory));
        String classpath = absoluteClasspath();
        Path executableJar = findExecutableBootJar(classpath);
        if (executableJar != null) {
            // An executable Spring Boot jar stores application classes and PDFBox in nested entries.
            Path launcherConfiguration = directory.resolve("loader.properties");
            Files.writeString(launcherConfiguration, "", StandardCharsets.UTF_8);
            command.add("-Dloader.main=" + WORKER_CLASS);
            command.add("-Dloader.home=" + directory);
            command.add("-Dloader.config.location=" + launcherConfiguration.toUri());
            command.addAll(List.of("-cp", executableJar.toString(), BOOT_LAUNCHER));
        } else {
            command.addAll(List.of("-cp", classpath, WORKER_CLASS));
        }
        command.add(input.toString());
        return command;
    }

    private String absoluteClasspath() {
        String configured = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return java.util.Arrays.stream(configured.split(Pattern.quote(File.pathSeparator), -1))
                .map(entry -> Path.of(entry.isEmpty() ? "." : entry).toAbsolutePath().normalize().toString())
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }

    private Path findExecutableBootJar(String classpath) throws IOException {
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            Path path = Path.of(entry);
            if (!entry.toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(path)) { continue; }
            try (ZipFile archive = new ZipFile(path.toFile())) {
                if (archive.getEntry("BOOT-INF/classes/" + WORKER_CLASS.replace('.', '/') + ".class") != null
                        && archive.getEntry(BOOT_LAUNCHER.replace('.', '/') + ".class") != null) { return path; }
            }
        }
        return null;
    }

    private void terminate(Process process) {
        if (process == null || !process.isAlive()) { return; }
        process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (process.isAlive() && System.nanoTime() < deadline) {
                try { process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
                catch (InterruptedException exception) { interrupted = true; process.destroyForcibly(); }
            }
            if (process.isAlive()) {
                throw unavailable("DIPLOMA_VALIDATION_UNAVAILABLE", "No se pudo finalizar la validación del diploma.");
            }
        } finally {
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    private void deleteTemporaryDirectory(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!root.getParent().equals(temporaryRoot) || !root.getFileName().toString().startsWith(TEMPORARY_DIRECTORY_PREFIX)) {
            throw new IOException("INVALID_TEMPORARY_DIRECTORY");
        }
        // Files.walk does not follow symbolic links. Every deleted path remains below this invocation's directory.
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.toAbsolutePath().normalize().startsWith(root)) { throw new IOException("INVALID_TEMPORARY_PATH"); }
                Files.deleteIfExists(path);
            }
        }
    }

    private static StaffOperationException tooLarge() {
        return new StaffOperationException(HttpStatus.PAYLOAD_TOO_LARGE, "DIPLOMA_TOO_LARGE", "El diploma no debe superar 5 MiB.");
    }
    private static StaffOperationException invalid(String detail) {
        return new StaffOperationException(HttpStatus.BAD_REQUEST, "INVALID_DIPLOMA", detail);
    }
    private static StaffOperationException unavailable(String code, String detail) {
        return new StaffOperationException(HttpStatus.SERVICE_UNAVAILABLE, code, detail);
    }
}
