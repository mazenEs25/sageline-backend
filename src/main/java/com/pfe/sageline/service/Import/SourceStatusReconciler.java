package com.pfe.sageline.service.Import;

import com.pfe.sageline.dtos.response.WarningDTO;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.SourceDeclaredStatus;
import com.pfe.sageline.enums.WarningCode;
import com.pfe.sageline.service.Import.MeasureMatcher.MatchedEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SourceStatusReconciler {

    /**
     * Reconciles parsed source status with computed authoritative status.
     * Emits STATUS_DIVERGENCE warnings where they disagree.
     */
    public List<WarningDTO> reconcile(List<MatchedEntry> matched, List<MeasureStatus> computedStatuses) {
        List<WarningDTO> warnings = new ArrayList<>();

        if (matched.size() != computedStatuses.size()) {
            log.warn("Matched entry count ({}) doesn't match computed status count ({})",
                    matched.size(), computedStatuses.size());
            return warnings;
        }

        for (int i = 0; i < matched.size(); i++) {
            MatchedEntry entry = matched.get(i);
            MeasureStatus computedStatus = computedStatuses.get(i);
            SourceDeclaredStatus sourceStatus = entry.parsedMeasure().sourceStatus();

            MeasureStatus declaredMeasureStatus = sourceStatusToMeasureStatus(sourceStatus);

            if (!declaredMeasureStatus.equals(computedStatus)) {
                String message = String.format("Status divergence on measure '%s': source declared=%s, computed=%s",
                        entry.resolvedCode(), declaredMeasureStatus, computedStatus);

                warnings.add(WarningDTO.builder()
                        .code(WarningCode.STATUS_DIVERGENCE)
                        .measureCode(entry.resolvedCode())
                        .message(message)
                        .build());

                // INFO-level so ops can spot catalog vs. station bound drift without DEBUG (T046).
                log.info("Status divergence: code={}, sourceDeclared={}, computed={}",
                        entry.resolvedCode(), declaredMeasureStatus, computedStatus);
            }
        }

        return warnings;
    }

    private MeasureStatus sourceStatusToMeasureStatus(SourceDeclaredStatus sourceStatus) {
        return switch (sourceStatus) {
            case OK_FROM_LOG -> MeasureStatus.OK;
            case OUT_OF_RANGE_FROM_LOG -> MeasureStatus.OUT_OF_RANGE;
            case NOT_EXECUTED_FROM_LOG -> MeasureStatus.NOT_EXECUTED;
        };
    }
}
