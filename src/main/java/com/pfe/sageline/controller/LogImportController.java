package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.LogImportOptionsDTO;
import com.pfe.sageline.dtos.response.LogImportReportDTO;
import com.pfe.sageline.service.Import.LogImportService;
import com.pfe.sageline.Config.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/validations/{validationId}")
@Slf4j
public class LogImportController {

    private final LogImportService logImportService;
    private final SecurityUtils securityUtils;

    public LogImportController(LogImportService logImportService, SecurityUtils securityUtils) {
        this.logImportService = logImportService;
        this.securityUtils = securityUtils;
    }

    @PostMapping(value = "/preview-log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TECH_VAL', 'TECH_PREP', 'ADMIN_IT')")
    public ResponseEntity<LogImportReportDTO> previewLog(
            @PathVariable Long validationId,
            @RequestPart("file") MultipartFile file) throws IOException {

        Long userId = securityUtils.getCurrentUserId();
        LogImportReportDTO report = logImportService.preview(validationId, file, userId);

        return ResponseEntity.ok(report);
    }

    @PostMapping(value = "/import-log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TECH_VAL', 'TECH_PREP', 'ADMIN_IT')")
    public ResponseEntity<LogImportReportDTO> importLog(
            @PathVariable Long validationId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "options", required = false) LogImportOptionsDTO options) throws IOException {

        Long userId = securityUtils.getCurrentUserId();
        if (options == null) {
            options = new LogImportOptionsDTO(false);
        }

        LogImportReportDTO report = logImportService.importLog(validationId, file, options, userId);

        return ResponseEntity.ok(report);
    }
}
