package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.response.*;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.service.Import.MeasureMatcher.MatchedEntry;
import com.pfe.sageline.service.Import.MeasureMatcher.UnmatchedEntry;
import com.pfe.sageline.service.Import.parser.ParsedLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LogImportReportMapper {

    public LogImportReportDTO toReport(
            ParsedLog parsedLog,
            List<MatchedEntry> matched,
            List<UnmatchedEntry> unmatched,
            List<WarningDTO> warnings,
            List<WouldOverwriteMeasureDTO> wouldOverwrite,
            List<MeasureStatus> computedStatuses,
            List<double[]> effectiveBounds,
            Long ticketId,
            boolean dryRun,
            boolean overwriteExisting) {

        List<MatchedMeasureDTO> matchedDTOs = new ArrayList<>();
        for (int i = 0; i < matched.size(); i++) {
            MatchedEntry entry = matched.get(i);
            MeasureStatus computedStatus = computedStatuses.get(i);
            double[] bounds = effectiveBounds.get(i);

            boolean inWouldOverwrite = wouldOverwrite.stream()
                    .anyMatch(o -> o.getMeasureCode().equals(entry.resolvedCode()));
            boolean willPersist = !inWouldOverwrite || overwriteExisting;

            String unit = entry.parsedMeasure().unit() != null
                    ? entry.parsedMeasure().unit()
                    : entry.template().getDefaultUnit();

            matchedDTOs.add(MatchedMeasureDTO.builder()
                    .measureCode(entry.resolvedCode())
                    .sourceCode(entry.parsedMeasure().code())
                    .measuredValue(entry.parsedMeasure().measuredValue())
                    .unit(unit)
                    .lowerBound(bounds[0])
                    .upperBound(bounds[1])
                    .computedStatus(computedStatus)
                    .sourceDeclaredStatus(entry.parsedMeasure().sourceStatus())
                    .templateId(entry.templateId())
                    .willPersist(willPersist)
                    .build());
        }

        List<UnmatchedMeasureDTO> unmatchedDTOs = unmatched.stream()
                .map(u -> UnmatchedMeasureDTO.builder()
                        .measureCode(u.code())
                        .measuredValue(u.measuredValue())
                        .unit(u.unit())
                        .reason(u.reason())
                        .reasonDetail(u.reasonDetail())
                        .build())
                .toList();

        int totalParsed = matched.size() + unmatched.size();

        return LogImportReportDTO.builder()
                .detectedFormat(parsedLog.format())
                .totalParsed(totalParsed)
                .matched(matchedDTOs)
                .unmatched(unmatchedDTOs)
                .wouldOverwrite(wouldOverwrite)
                .warnings(warnings)
                .ticketId(ticketId)
                .dryRun(dryRun)
                .build();
    }
}
