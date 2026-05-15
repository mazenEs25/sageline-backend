package com.pfe.sageline.service.Import.parser;

import java.nio.file.Path;

public record LogImportCommand(
    Long validationId,
    String originalFilename,
    Path storedPath,
    byte[] bytesForParsing,
    boolean overwriteExisting,
    Long uploadedBy
) {}
