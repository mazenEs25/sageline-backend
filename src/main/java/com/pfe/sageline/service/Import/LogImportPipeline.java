package com.pfe.sageline.service.Import;

import com.pfe.sageline.dtos.response.*;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.LogImportReportMapper;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.service.Import.MeasureMatcher;
import com.pfe.sageline.service.Import.SourceStatusReconciler;
import com.pfe.sageline.service.Import.parser.*;
import com.pfe.sageline.service.MeasureDeviationCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LogImportPipeline {

    private final HeaderSniffer sniffer;
    private final MeasureMatcher matcher;
    private final MeasureDeviationCalculator deviationCalculator;
    private final SourceStatusReconciler reconciler;
    private final ValidationMeasureRepository measureRepository;
    private final LogImportReportMapper reportMapper;
    private final ValidationRepository validationRepository;

    public LogImportPipeline(
            HeaderSniffer sniffer,
            MeasureMatcher matcher,
            MeasureDeviationCalculator deviationCalculator,
            SourceStatusReconciler reconciler,
            ValidationMeasureRepository measureRepository,
            LogImportReportMapper reportMapper,
            ValidationRepository validationRepository) {
        this.sniffer = sniffer;
        this.matcher = matcher;
        this.deviationCalculator = deviationCalculator;
        this.reconciler = reconciler;
        this.measureRepository = measureRepository;
        this.reportMapper = reportMapper;
        this.validationRepository = validationRepository;
    }

    public LogImportReportDTO run(LogImportCommand cmd, boolean dryRun) {
        String content = new String(cmd.bytesForParsing(), StandardCharsets.UTF_8);
        List<WarningDTO> warnings = new ArrayList<>();
        List<WouldOverwriteMeasureDTO> wouldOverwrite = new ArrayList<>();

        Validation validation = validationRepository.findById(cmd.validationId())
                .orElseThrow(() -> new ResourceNotFoundException("Validation not found: " + cmd.validationId()));

        ValidationZone zone = validation.getValidationZone();
        if (zone == null) {
            throw new ResourceNotFoundException("Validation " + cmd.validationId() + " has no zone — cannot determine PosteType");
        }
        PosteType posteType = zone.getPosteType();

        // 1. Sniff and parse
        LogFormatStrategy strategy = sniffer.sniff(content);
        ParsedLog parsedLog = strategy.parse(content);

        // 2. Check format matches PosteType
        checkFormatMatchesPosteType(parsedLog.format(), posteType, warnings);

        // 3. Match measures
        MeasureMatcher.MatchResult matchResult = matcher.match(posteType, parsedLog.measures());
        List<MeasureMatcher.MatchedEntry> matched = matchResult.matched();
        List<MeasureMatcher.UnmatchedEntry> unmatched = matchResult.unmatched();

        // 4. Compute authoritative statuses using effective bounds (parsed bounds
        //    when present, else the catalog template's defaults — Constitution II:
        //    every measure persisted MUST carry bounds).
        List<MeasureStatus> computedStatuses = new ArrayList<>();
        List<double[]> effectiveBounds = new ArrayList<>();
        for (MeasureMatcher.MatchedEntry entry : matched) {
            ParsedMeasure pm = entry.parsedMeasure();
            double lo = pm.lowerBound() != null ? pm.lowerBound() : entry.template().getDefaultLowerBound();
            double hi = pm.upperBound() != null ? pm.upperBound() : entry.template().getDefaultUpperBound();
            effectiveBounds.add(new double[]{lo, hi});
            MeasureStatus status;
            try {
                status = deviationCalculator.computeStatus(pm.measuredValue(), lo, hi);
            } catch (IllegalStateException e) {
                // Degenerate bounds (lo>=hi) — treat as NOT_EXECUTED rather than failing the whole import.
                log.warn("Degenerate bounds for code {}: [{}, {}] — marking NOT_EXECUTED", entry.resolvedCode(), lo, hi);
                status = MeasureStatus.NOT_EXECUTED;
            }
            computedStatuses.add(status);
        }

        // 5. Reconcile source vs computed status
        List<WarningDTO> statusWarnings = reconciler.reconcile(matched, computedStatuses);
        warnings.addAll(statusWarnings);

        // 6. Compute wouldOverwrite — runs for BOTH preview and commit so the report is
        //    identical across modes (FR-015 / SC-003). The overwriteExisting flag is then
        //    honored downstream by the mapper (willPersist) and by the service (persistence).
        if (!matched.isEmpty()) {
            Collection<String> matchedCodes = matched.stream()
                    .map(MeasureMatcher.MatchedEntry::resolvedCode)
                    .collect(Collectors.toList());

            List<ValidationMeasure> existing = measureRepository
                    .findByValidationIdAndMeasureCodeIn(cmd.validationId(), matchedCodes);

            for (int i = 0; i < matched.size(); i++) {
                MeasureMatcher.MatchedEntry entry = matched.get(i);
                ParsedMeasure pm = entry.parsedMeasure();
                MeasureStatus computedStatus = computedStatuses.get(i);

                Optional<ValidationMeasure> existingRow = existing.stream()
                        .filter(m -> m.getMeasureCode().equals(entry.resolvedCode()))
                        .findFirst();

                if (existingRow.isPresent()) {
                    ValidationMeasure vm = existingRow.get();
                    if (vm.getStatus() == MeasureStatus.OK || vm.getStatus() == MeasureStatus.OUT_OF_RANGE) {
                        wouldOverwrite.add(WouldOverwriteMeasureDTO.builder()
                                .measureCode(entry.resolvedCode())
                                .currentValue(vm.getMeasuredValue())
                                .currentStatus(vm.getStatus())
                                .currentEnteredManually(vm.getImportedLogFile() == null)
                                .newValue(pm.measuredValue())
                                .newComputedStatus(computedStatus)
                                .build());
                    }
                }
            }
        }

        // 7. Build report (preview always has dryRun=true in the response, commit has dryRun=false)
        return reportMapper.toReport(
                parsedLog,
                matched,
                unmatched,
                warnings,
                wouldOverwrite,
                computedStatuses,
                effectiveBounds,
                cmd.validationId(),
                dryRun,
                cmd.overwriteExisting()
        );
    }

    private void checkFormatMatchesPosteType(LogFormat format, PosteType posteType, List<WarningDTO> warnings) {
        // BTF target PosteType pinned to ACC per T044. Future BTF-equivalent postes
        // can be added to the BTF allow-set here.
        boolean matches = switch (format) {
            case BNFT -> posteType == PosteType.TEST_FONCTIONNEL;
            case BWC -> posteType == PosteType.WIFI_CONDUIT || posteType == PosteType.BANC_WIFI_CONDUIT;
            case BTF -> posteType == PosteType.ACC;
        };

        if (!matches) {
            warnings.add(WarningDTO.builder()
                    .code(WarningCode.WRONG_STATION_FORMAT)
                    .message("Detected format " + format + " may not match poste type " + posteType)
                    .build());
        }
    }
}
