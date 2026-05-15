package com.pfe.sageline.service.Import;

import com.pfe.sageline.Config.LogImportProperties;
import com.pfe.sageline.dtos.request.LogImportOptionsDTO;
import com.pfe.sageline.dtos.response.LogImportReportDTO;
import com.pfe.sageline.dtos.response.MatchedMeasureDTO;
import com.pfe.sageline.entity.ImportedLogFile;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.enums.LogFormat;
import com.pfe.sageline.exception.LogTooLargeException;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.repository.ImportedLogFileRepository;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.service.Import.parser.LogImportCommand;
import com.pfe.sageline.service.MeasureDeviationCalculator;
import com.pfe.sageline.service.MeasureEditabilityGuard;
import com.pfe.sageline.service.workflow.WorkflowReadinessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the full log import lifecycle.
 * Invariant chain: size check → editability → lock → parse → match → persist.
 *
 * <p>Preview and commit share {@link LogImportPipeline}; their reports are equivalent
 * by construction (SC-003). Persistence is gated by {@code @Transactional}: the
 * advisory lock acquired via {@link ImportLockService} is bound to the same transaction,
 * the readiness STOMP publish is registered as an {@code afterCommit} synchronization,
 * and on rollback the on-disk file is deleted.
 */
@Service
@Slf4j
public class LogImportService {

    private final LogImportPipeline pipeline;
    private final LogStorageService storage;
    private final ImportLockService lockService;
    private final ImportedLogFileRepository importedLogFileRepository;
    private final ValidationMeasureRepository measureRepository;
    private final PosteMeasureCatalogRepository catalogRepository;
    private final MeasureDeviationCalculator deviationCalculator;
    private final MeasureEditabilityGuard editabilityGuard;
    private final WorkflowReadinessService readinessService;
    private final LogImportProperties properties;
    private final ValidationRepository validationRepository;

    public LogImportService(
            LogImportPipeline pipeline,
            LogStorageService storage,
            ImportLockService lockService,
            ImportedLogFileRepository importedLogFileRepository,
            ValidationMeasureRepository measureRepository,
            PosteMeasureCatalogRepository catalogRepository,
            MeasureDeviationCalculator deviationCalculator,
            MeasureEditabilityGuard editabilityGuard,
            WorkflowReadinessService readinessService,
            LogImportProperties properties,
            ValidationRepository validationRepository) {
        this.pipeline = pipeline;
        this.storage = storage;
        this.lockService = lockService;
        this.importedLogFileRepository = importedLogFileRepository;
        this.measureRepository = measureRepository;
        this.catalogRepository = catalogRepository;
        this.deviationCalculator = deviationCalculator;
        this.editabilityGuard = editabilityGuard;
        this.readinessService = readinessService;
        this.properties = properties;
        this.validationRepository = validationRepository;
    }

    @Transactional
    public LogImportReportDTO preview(Long validationId, MultipartFile file, Long userId) throws IOException {
        Validation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation not found: " + validationId));

        byte[] bytes = file.getBytes();
        checkSize(bytes.length);
        editabilityGuard.requireEditable(validation);

        try (ImportLockService.LockHandle h = lockService.tryAcquire(validationId)) {
            LogImportCommand cmd = new LogImportCommand(
                    validationId,
                    file.getOriginalFilename(),
                    null,
                    bytes,
                    false,
                    userId
            );
            return pipeline.run(cmd, true);
        }
    }

    @Transactional
    public LogImportReportDTO importLog(Long validationId, MultipartFile file,
                                        LogImportOptionsDTO options, Long userId) throws IOException {
        if (options == null) {
            options = new LogImportOptionsDTO(false);
        }

        Validation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation not found: " + validationId));

        byte[] bytes = file.getBytes();
        checkSize(bytes.length);
        editabilityGuard.requireEditable(validation);

        try (ImportLockService.LockHandle h = lockService.tryAcquire(validationId)) {
            // Write file to disk BEFORE the persistence step so the bytes are
            // recoverable for audit even if the DB transaction rolls back.
            Path storedPath = storage.persistUpload(validationId, file.getOriginalFilename(), bytes);

            LogImportCommand cmd = new LogImportCommand(
                    validationId,
                    file.getOriginalFilename(),
                    storedPath,
                    bytes,
                    options.isOverwriteExisting(),
                    userId
            );

            LogImportReportDTO report = pipeline.run(cmd, false);

            ImportedLogFile importedLogFile = ImportedLogFile.builder()
                    .validation(validation)
                    .originalName(file.getOriginalFilename())
                    .detectedFormat(LogFormat.valueOf(report.getDetectedFormat().name()))
                    .storedPath(storedPath.toString())
                    .sizeBytes((long) bytes.length)
                    .uploadedBy(userId)
                    .overwriteExisting(options.isOverwriteExisting())
                    .build();
            importedLogFileRepository.save(importedLogFile);

            persistMeasures(validation, importedLogFile, report, userId);

            // Register transaction synchronisations:
            //  - afterCommit: publish readiness snapshot (subscribers see post-import state).
            //  - afterRollback (no commit): delete the on-disk file so disk and DB stay aligned.
            registerTxSync(validationId, storedPath);

            return report;
        }
    }

    private void persistMeasures(Validation validation, ImportedLogFile importedLogFile,
                                 LogImportReportDTO report, Long userId) {
        if (report.getMatched() == null || report.getMatched().isEmpty()) {
            return;
        }

        // Bulk-fetch the catalog templates referenced by the matched DTOs (one query per id
        // would be N+1; collect ids first, then fetch in one round-trip via findAllById).
        Map<Long, PosteMeasureCatalog> templatesById = new HashMap<>();
        report.getMatched().stream()
                .map(MatchedMeasureDTO::getTemplateId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(id -> catalogRepository.findById(id).ifPresent(t -> templatesById.put(id, t)));

        for (MatchedMeasureDTO matched : report.getMatched()) {
            if (matched.getWillPersist() == null || !matched.getWillPersist()) {
                continue;
            }

            PosteMeasureCatalog template = templatesById.get(matched.getTemplateId());
            if (template == null) {
                log.warn("Skipping import of measure {} — catalog template {} not found",
                        matched.getMeasureCode(), matched.getTemplateId());
                continue;
            }

            Double deviationPct = deviationCalculator.computeDeviationPct(
                    matched.getMeasuredValue(),
                    matched.getLowerBound(),
                    matched.getUpperBound()
            );

            // Upsert: update an existing row matched by measureCode, else insert.
            Optional<ValidationMeasure> existing = measureRepository
                    .findByValidationIdAndMeasureCodeIn(validation.getId(), java.util.List.of(matched.getMeasureCode()))
                    .stream().findFirst();

            ValidationMeasure vm;
            if (existing.isPresent()) {
                vm = existing.get();
                vm.setMeasuredValue(matched.getMeasuredValue());
                vm.setLowerBound(matched.getLowerBound());
                vm.setUpperBound(matched.getUpperBound());
                vm.setUnit(matched.getUnit());
                vm.setStatus(matched.getComputedStatus());
                vm.setDeviationPct(deviationPct);
                vm.setSourceDeclaredStatus(matched.getSourceDeclaredStatus());
                vm.setImportedLogFile(importedLogFile);
                vm.setSourceLogFile(importedLogFile.getOriginalName());
                vm.setMeasuredAt(Instant.now());
            } else {
                vm = ValidationMeasure.builder()
                        .validation(validation)
                        .catalogTemplate(template)
                        .measureCode(matched.getMeasureCode())
                        .measureLabel(template.getMeasureLabel())
                        .category(template.getCategory())
                        .unit(matched.getUnit())
                        .lowerBound(matched.getLowerBound())
                        .upperBound(matched.getUpperBound())
                        .measuredValue(matched.getMeasuredValue())
                        .status(matched.getComputedStatus())
                        .deviationPct(deviationPct)
                        .sourceDeclaredStatus(matched.getSourceDeclaredStatus())
                        .importedLogFile(importedLogFile)
                        .sourceLogFile(importedLogFile.getOriginalName())
                        .enteredBy(userId)
                        .measuredAt(Instant.now())
                        .mandatoryAtCreation(Boolean.TRUE.equals(template.getMandatory()))
                        .build();
            }
            measureRepository.save(vm);
        }
    }

    private void registerTxSync(Long validationId, Path storedPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No active transaction — fall back to immediate publish (preview path
            // should never reach here; this branch is purely defensive).
            readinessService.publishSnapshot(validationId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                readinessService.publishSnapshot(validationId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Import transaction rolled back for validation {} — deleting orphaned log at {}",
                            validationId, storedPath);
                    storage.delete(storedPath);
                }
            }
        });
    }

    private void checkSize(long sizeBytes) {
        long maxBytes = properties.getMaxFileSize().toBytes();
        if (sizeBytes > maxBytes) {
            throw new LogTooLargeException(
                    "File size " + sizeBytes + " exceeds maximum " + maxBytes,
                    sizeBytes,
                    maxBytes
            );
        }
    }
}
