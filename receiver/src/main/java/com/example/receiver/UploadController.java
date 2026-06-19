package com.example.receiver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The single endpoint of the receiver POC.
 *
 * <p><b>The whole design goal:</b> accept a very large file (e.g. 10 GB) while
 * keeping heap usage tiny (~100 MB budget). We achieve that by reading the raw
 * request body as a stream ({@link HttpServletRequest#getInputStream()}) and
 * copying it to disk in a small, fixed buffer. The file is <b>never</b> held in
 * memory, and we never use multipart (which would buffer the part first).
 *
 * <p>Contract with the sender:
 * <ul>
 *   <li>{@code POST /upload}</li>
 *   <li>body: raw bytes, {@code Content-Type: application/octet-stream}</li>
 *   <li>header {@code X-File-Name}: the original file name (sanitized here)</li>
 * </ul>
 */
@RestController
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    /** Bounded copy buffer. This is essentially all the memory a transfer uses. */
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    public static final String FILE_NAME_HEADER = "X-File-Name";

    private final Path outputDir;

    public UploadController(@Value("${receiver.output-dir:./received}") String outputDir) {
        this.outputDir = Paths.get(outputDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureOutputDir() throws IOException {
        Files.createDirectories(outputDir);
        log.info("Receiver will store uploads in {}", outputDir);
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResult> upload(@RequestHeader(value = FILE_NAME_HEADER, required = false) String fileNameHeader,
                                               HttpServletRequest request) throws IOException {
        String fileName = sanitize(fileNameHeader);
        Path target = outputDir.resolve(fileName);
        // Write to a temporary ".part" file first; only rename to the final name on
        // success, so a half-received file is never mistaken for a complete one.
        Path temp = outputDir.resolve(fileName + ".part");

        log.info("Receiving '{}' -> {}", fileName, target);
        long start = System.nanoTime();
        long total = 0;

        // try-with-resources guarantees streams are closed even on error.
        try (InputStream in = request.getInputStream();
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read); // chunk goes straight to disk; nothing accumulates in memory
                total += read;
            }
            out.flush();
        } catch (IOException e) {
            Files.deleteIfExists(temp); // don't leave partial files around
            log.error("Upload of '{}' failed after {} bytes", fileName, total, e);
            throw e;
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("Stored '{}' ({} bytes) in {} ms", fileName, total, elapsedMs);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadResult("OK", total, target.toString(), elapsedMs));
    }

    /**
     * Strips any path information from the supplied name to avoid path-traversal,
     * and falls back to a timestamped name if none was provided.
     */
    private static String sanitize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "upload-" + System.currentTimeMillis() + ".bin";
        }
        // Keep only the final path segment, drop separators and other risky chars.
        String name = Paths.get(rawName).getFileName().toString();
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "upload-" + System.currentTimeMillis() + ".bin" : name;
    }
}

