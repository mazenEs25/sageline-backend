package com.pfe.sageline.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static guarantee: every setStatus(TicketStatus.EN_REVUE) call in main sources
 * must be guarded by a transitionGuard.check(...) within the preceding 8 lines.
 * Only ValidationService.java is allowed to contain this assignment.
 */
class SubmitReviewGuardArchitectureTest {

    private static final String MAIN_SRC = "src/main/java/com/pfe/sageline";
    private static final String TARGET_LITERAL = "setStatus(TicketStatus.EN_REVUE)";
    private static final String GUARD_LITERAL = "transitionGuard.check(";
    private static final String ALLOWED_FILE = "ValidationService.java";

    @Test
    void only_ValidationService_sets_status_to_EN_REVUE() throws IOException {
        Path srcRoot = Paths.get(MAIN_SRC);
        if (!srcRoot.toFile().exists()) {
            // Running from project root
            srcRoot = Paths.get("sageLine-backend", MAIN_SRC);
        }

        List<String> violatingFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(srcRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !p.getFileName().toString().equals(ALLOWED_FILE))
                 .forEach(p -> {
                     try {
                         String content = Files.readString(p);
                         if (content.contains(TARGET_LITERAL)) {
                             violatingFiles.add(p.toString());
                         }
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }

        assertThat(violatingFiles)
            .as("Only ValidationService.java should call setStatus(TicketStatus.EN_REVUE). Found in: " + violatingFiles)
            .isEmpty();
    }

    @Test
    void every_EN_REVUE_transition_in_ValidationService_is_preceded_by_guard() throws IOException {
        Path filePath = Paths.get(MAIN_SRC, "service", "ValidationService.java");
        if (!filePath.toFile().exists()) {
            filePath = Paths.get("sageLine-backend", MAIN_SRC, "service", "ValidationService.java");
        }

        List<String> lines = Files.readAllLines(filePath);
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(TARGET_LITERAL)) {
                // Check the 8 preceding lines for transitionGuard.check(
                int start = Math.max(0, i - 8);
                boolean guardPresent = false;
                for (int j = start; j < i; j++) {
                    if (lines.get(j).contains(GUARD_LITERAL)) {
                        guardPresent = true;
                        break;
                    }
                }
                if (!guardPresent) {
                    violations.add("Line " + (i + 1) + ": " + lines.get(i).trim()
                        + " — no transitionGuard.check() found in preceding 8 lines");
                }
            }
        }

        assertThat(violations)
            .as("Every setStatus(EN_REVUE) in ValidationService must be preceded by transitionGuard.check()")
            .isEmpty();
    }
}
