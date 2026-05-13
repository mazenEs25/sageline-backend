package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.internal.MandatoryCoverageRow;
import com.pfe.sageline.dtos.response.MissingMeasureDTO;
import com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowReadinessService {

    private final ValidationRepository validationRepository;
    private final ValidationMeasureRepository measureRepository;
    private final SourceStatusRule sourceStatusRule;
    private final MandatoryMeasureCoverageRule coverageRule;

    @Transactional(readOnly = true)
    public WorkflowReadinessDTO computeReadiness(Long validationId, TicketStatus targetStatus) {
        Validation ticket = validationRepository.findById(validationId)
            .orElseThrow(() -> new ResourceNotFoundException("Validation " + validationId + " not found"));
        TicketStatus target = (targetStatus != null) ? targetStatus : TicketStatus.EN_REVUE;

        List<MandatoryCoverageRow> rows = measureRepository.coverageSummary(validationId);
        int mandatoryTotal  = (int) rows.stream().filter(MandatoryCoverageRow::mandatory).mapToLong(MandatoryCoverageRow::count).sum();
        int mandatoryFilled = (int) rows.stream().filter(r -> r.mandatory() && r.status() != MeasureStatus.NOT_EXECUTED).mapToLong(MandatoryCoverageRow::count).sum();
        int mandatoryMissing = mandatoryTotal - mandatoryFilled;

        List<MissingMeasureDTO> missing = (mandatoryMissing == 0)
            ? List.of()
            : measureRepository.missingMandatoryMeasures(validationId);
        List<OutOfRangeMeasureDTO> outOfRange = measureRepository.outOfRangeMeasures(validationId);

        List<String> reasons = new ArrayList<>();
        RuleVerdict v1 = sourceStatusRule.evaluate(ticket, target);
        if (!v1.allowed()) reasons.addAll(v1.blockingReasons());
        RuleVerdict v2 = coverageRule.evaluate(ticket, target);
        if (!v2.allowed()) reasons.addAll(v2.blockingReasons());
        boolean canTransition = reasons.isEmpty();

        return new WorkflowReadinessDTO(
            validationId, ticket.getStatus().name(), target.name(),
            mandatoryTotal, mandatoryFilled, mandatoryMissing,
            missing, outOfRange, canTransition, List.copyOf(reasons));
    }
}
