package com.pfe.sageline.service.Import;

import com.pfe.sageline.Config.LogImportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LogStorageService {

    private final LogImportProperties properties;

    public LogStorageService(LogImportProperties properties) {
        this.properties = properties;
    }

    /**
     * Persists the uploaded file to disk under {storageRoot}/{validationId}/{epochMs}_{safeName}.
     * The epoch prefix avoids overwriting prior imports of the same filename (H8).
     * The filename is sanitised to its base name to prevent path traversal (H7).
     */
    public Path persistUpload(Long validationId, String originalName, byte[] bytes) {
        String safeName = sanitizeFilename(originalName);
        String prefixed = System.currentTimeMillis() + "_" + safeName;
        try {
            Path storagePath = Paths.get(properties.getStorageRoot())
                    .resolve(validationId.toString())
                    .resolve(prefixed);

            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, bytes);

            log.info("Persisted log file for validation {} to {}", validationId, storagePath);
            return storagePath;
        } catch (IOException e) {
            log.error("Failed to persist log file for validation {}: {}", validationId, e.getMessage());
            throw new RuntimeException("Failed to persist log file: " + e.getMessage(), e);
        }
    }

    /**
     * Reduces a user-supplied filename to a safe base name. Strips any directory
     * components, rejects names containing ".." or path separators, and falls back
     * to "upload.log" on degenerate input.
     */
    static String sanitizeFilename(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "upload.log";
        }
        String base = Paths.get(originalName).getFileName().toString();
        if (base.isBlank() || base.contains("..") || base.contains("/") || base.contains("\\")) {
            throw new IllegalArgumentException("Invalid upload filename: " + originalName);
        }
        return base;
    }

    /**
     * Deletes a file from disk (best-effort).
     */
    public void delete(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                log.info("Deleted log file: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to delete log file {}: {}", path, e.getMessage());
        }
    }

    /**
     * Reads a snippet of the file around the measure code. Match is anchored on
     * either {@code Mesure <code>} or a word-boundary occurrence of the code, so
     * codes that are prefixes of other codes don't false-positive (H10).
     *
     * Returns null when the file is missing on disk OR when the measure code is
     * absent from the file — both cases are surfaced to callers as
     * {@code available=false} (H9), preventing an empty-snippet "success" response.
     */
    public SnippetResult readSnippetAround(Path path, String measureCode, int snippetLines) {
        try {
            if (!Files.exists(path)) {
                log.debug("Log file not found on disk: {}", path);
                return null;
            }

            List<String> allLines = Files.readAllLines(path);
            // Try the Plan.md Sagemcom-stream format first, then the supervisor-fixture
            // "Measure: CODE" format, then a generic word-boundary match.
            String streamMarker = "Mesure <" + measureCode + ">";
            String fixtureMarker = "Measure: " + measureCode;
            Pattern wordBoundary = Pattern.compile("\\b" + Pattern.quote(measureCode) + "\\b");

            int targetLineIndex = -1;
            for (int i = 0; i < allLines.size(); i++) {
                String line = allLines.get(i);
                if (line.contains(streamMarker)
                        || line.contains(fixtureMarker)
                        || wordBoundary.matcher(line).find()) {
                    targetLineIndex = i;
                    break;
                }
            }

            if (targetLineIndex == -1) {
                log.debug("Measure code {} not found in log file {}", measureCode, path);
                return null;
            }

            int startLine = Math.max(0, targetLineIndex - snippetLines);
            int endLine = Math.min(allLines.size() - 1, targetLineIndex + snippetLines);
            String snippet = String.join("\n", allLines.subList(startLine, endLine + 1));
            return new SnippetResult(snippet, startLine + 1, endLine + 1);
        } catch (IOException e) {
            log.error("Failed to read snippet from {}: {}", path, e.getMessage());
            return null;
        }
    }

    public record SnippetResult(String text, int startLine, int endLine) {}
}
