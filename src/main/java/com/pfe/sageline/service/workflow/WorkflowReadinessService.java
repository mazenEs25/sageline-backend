package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.internal.MandatoryCoverageRow;
import com.pfe.sageline.dtos.response.MissingMeasureDTO;
import com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationPosteStatus;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationPosteStatusRepository;
import com.pfe.sageline.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowReadinessService {

    private final ValidationRepository validationRepository;
    private final ValidationMeasureRepository measureRepository;
    private final ValidationPosteStatusRepository posteStatusRepository;
    private final SourceStatusRule sourceStatusRule;
    private final MandatoryMeasureCoverageRule coverageRule;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Computes the current readiness snapshot and pushes it to
     * <code>/topic/validation/{id}/readiness</code>. Caller decides timing
     * (e.g., post-commit).
     *
     * <p><b>Topic naming:</b> slash-separated to match the canonical form used by the
     * frontend ({@code ticket-detail.component.ts}) and the test scenario document.
     * Earlier versions used dot-separators ({@code /topic/validation.{id}.readiness})
     * which caused the WorkflowReadinessBar to never receive live updates.</p>
     *
     * <p>STOMP failures are logged and swallowed — readiness data is best-effort.</p>
     */
    public void publishSnapshot(Long validationId) {
        try {
            WorkflowReadinessDTO snapshot = computeReadiness(validationId, null);
            messagingTemplate.convertAndSend(
                "/topic/validation/" + validationId + "/readiness", snapshot);
        } catch (Exception e) {
            log.warn("Readiness STOMP push failed for ticket {}: {}", validationId, e.getMessage());
        }
    }

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

    /**
     * Per-poste readiness — used by the Clôturer button on each poste of the line.
     * Returns the same {@link WorkflowReadinessDTO} shape as the ticket-level
     * readiness so the frontend can reuse the same model/component.
     *
     * <p>{@code canTransition} here means "this poste can be closed":
     * every mandatory measure attached to this {@link ValidationPosteStatus} must
     * have a status ≠ NOT_EXECUTED. OUT_OF_RANGE rows are listed but do <b>not</b>
     * block — closing the poste with an OUT_OF_RANGE measure simply produces a
     * NON_CONFORME verdict for the poste, which is still valid closure.</p>
     */
    @Transactional(readOnly = true)
    public WorkflowReadinessDTO computePosteReadiness(Long validationId, Long zoneId) {
        Validation ticket = validationRepository.findById(validationId)
            .orElseThrow(() -> new ResourceNotFoundException("Validation " + validationId + " not found"));

        ValidationPosteStatus ps = posteStatusRepository
            .findByValidationIdAndZoneId(validationId, zoneId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Poste status not found for validation=" + validationId + " zone=" + zoneId));

        List<MandatoryCoverageRow> rows = measureRepository.coverageSummaryByPosteStatus(ps.getId());
        int mandatoryTotal  = (int) rows.stream().filter(MandatoryCoverageRow::mandatory).mapToLong(MandatoryCoverageRow::count).sum();
        int mandatoryFilled = (int) rows.stream().filter(r -> r.mandatory() && r.status() != MeasureStatus.NOT_EXECUTED).mapToLong(MandatoryCoverageRow::count).sum();
        int mandatoryMissing = mandatoryTotal - mandatoryFilled;

        List<MissingMeasureDTO> missing = (mandatoryMissing == 0)
            ? List.of()
            : measureRepository.missingMandatoryMeasuresOnPoste(ps.getId());
        List<OutOfRangeMeasureDTO> outOfRange = measureRepository.outOfRangeMeasuresOnPoste(ps.getId());

        List<String> reasons = new ArrayList<>();
        // Poste cannot close while the ticket isn't editable.
        if (ticket.getStatus() != TicketStatus.EN_COURS
                && ticket.getStatus() != TicketStatus.EN_REVUE) {
            reasons.add("Ticket status is " + ticket.getStatus() + " — postes can only be closed while EN_COURS or EN_REVUE");
        }
        // Poste cannot close twice.
        if (ps.getStatus() == TicketStatus.CONFORME || ps.getStatus() == TicketStatus.NON_CONFORME) {
            reasons.add("Poste already closed (" + ps.getStatus() + ")");
        }
        // Mandatory measures must be filled.
        if (mandatoryMissing > 0) {
            reasons.add(mandatoryMissing + " mandatory measure(s) still in NOT_EXECUTED state on this poste");
        }
        boolean canTransition = reasons.isEmpty();

        return new WorkflowReadinessDTO(
            validationId,
            ps.getStatus().name(),
            "CLOSED",  // "closed" is the target for a poste; not a real TicketStatus value
            mandatoryTotal, mandatoryFilled, mandatoryMissing,
            missing, outOfRange, canTransition, List.copyOf(reasons));
    }
}
