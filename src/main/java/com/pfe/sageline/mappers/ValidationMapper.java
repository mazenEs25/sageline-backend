package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.TicketCreateRequestDTO;
import com.pfe.sageline.dtos.response.PosteStatusDTO;
import com.pfe.sageline.dtos.response.ValidationResponseDTO;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.TicketStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ValidationMapper {

    @Autowired
    private ValidationAssignmentMapper assignmentMapper;

    @Autowired
    private com.pfe.sageline.repository.ValidationMeasureRepository measureRepository;

    /**
     * Build a line-level ticket entity. {@code line} is the canonical owner of
     * the ticket (mandatory since 2026-04). {@code primaryZone} is the poste we
     * copy into the legacy {@code validationZone} FK so that AI prediction and
     * zone-scoped KPI code keep working while we migrate the call-sites.
     */
    public Validation toEntity(TicketCreateRequestDTO dto,
                               ProductionLine line,
                               ValidationZone primaryZone,
                               User createdBy,
                               String ticketCode) {
        return Validation.builder()
                .ticketCode(ticketCode)
                .status(TicketStatus.PLANIFIE)
                .productionLine(line)
                .validationZone(primaryZone) // legacy compat, never null for new tickets
                .createdBy(createdBy)
                .plannedDate(dto.getPlannedDate())
                .plannedWeekStart(dto.getPlannedWeekStart())
                .plannedWeekEnd(dto.getPlannedWeekEnd())
                .priority(dto.getPriority() != null ? dto.getPriority() :
                        com.pfe.sageline.enums.Priority.NORMALE)
                .comments(dto.getComments())
                .build();
    }

    public ValidationResponseDTO toResponseDTO(Validation entity) {
        // Prefer the new direct FK; fall back to the legacy zone->line chain
        // for any row that hasn't been migrated yet.
        ProductionLine line = entity.getProductionLine();
        ValidationZone zone = entity.getValidationZone();
        if (line == null && zone != null) {
            line = zone.getProductionLine();
        }
        Phase phase = line != null ? line.getPhase() : null;
        Secteur secteur = phase != null ? phase.getSecteur() : null;

        // Calculate results stats
        int resultsCount = entity.getValidationResults() != null ?
                entity.getValidationResults().size() : 0;
        long conformCount = entity.getValidationResults() != null ?
                entity.getValidationResults().stream()
                .filter(r -> Boolean.TRUE.equals(r.getConform()))
                .count() : 0;
        int nonConformCount = resultsCount - (int) conformCount;
        Double conformityRate = resultsCount > 0 ?
                (conformCount * 100.0 / resultsCount) : null;

        // AI prediction
        NonConformityPrediction prediction = entity.getPrediction();

        // Group legacy results by poste (zoneId) for back-compat with
        // tickets that still use the old validation_results table.
        Map<Long, long[]> resultsByZone = new HashMap<>();
        if (entity.getValidationResults() != null) {
            for (ValidationResult r : entity.getValidationResults()) {
                if (r.getZone() == null) continue; // legacy / unscoped
                long[] counters = resultsByZone.computeIfAbsent(
                        r.getZone().getId(), k -> new long[]{0L, 0L});
                counters[0]++; // total
                if (Boolean.FALSE.equals(r.getConform())) counters[1]++; // non-conform
            }
        }

        // Per-poste measure counts from validation_measures (the new table).
        // Maps posteStatusId → [totalMeasures, outOfRangeMeasures].
        Map<Long, long[]> measuresByPosteStatusId = new HashMap<>();
        List<Object[]> measureCounts = measureRepository.countMeasuresByPosteStatus(entity.getId());
        for (Object[] row : measureCounts) {
            Long psId = (Long) row[0];
            long total = (Long) row[1];
            long oor = (Long) row[2];
            measuresByPosteStatusId.put(psId, new long[]{total, oor});
        }

        // Per-poste sub-statuses — ordered by orderInLine, nulls last
        List<ValidationPosteStatus> rawPosteStatuses = entity.getPosteStatuses();
        List<PosteStatusDTO> posteStatusDtos = rawPosteStatuses == null
                ? Collections.emptyList()
                : rawPosteStatuses.stream()
                    .sorted(Comparator.comparing(
                            ValidationPosteStatus::getOrderInLine,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ValidationPosteStatus::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> {
                        PosteStatusDTO d = toPosteStatusDTO(p);
                        if (d != null) {
                            // Prefer counts from the new validation_measures table.
                            long[] mc = measuresByPosteStatusId.getOrDefault(p.getId(), null);
                            if (mc != null) {
                                d.setResultsCount(mc[0]);
                                d.setNonConformCount(mc[1]);
                            } else if (d.getZoneId() != null) {
                                // Fallback to legacy validation_results counts.
                                long[] c = resultsByZone.getOrDefault(
                                        d.getZoneId(), new long[]{0L, 0L});
                                d.setResultsCount(c[0]);
                                d.setNonConformCount(c[1]);
                            }
                        }
                        return d;
                    })
                    .toList();

        int posteTotal = posteStatusDtos.size();
        int posteConforme = (int) posteStatusDtos.stream()
                .filter(p -> p.getStatus() == TicketStatus.CONFORME).count();
        int posteNonConforme = (int) posteStatusDtos.stream()
                .filter(p -> p.getStatus() == TicketStatus.NON_CONFORME).count();
        int posteDone = posteConforme + posteNonConforme;

        return ValidationResponseDTO.builder()
                .id(entity.getId())
                .ticketCode(entity.getTicketCode())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                // Zone
                .validationZoneId(zone != null ? zone.getId() : null)
                .zoneName(zone != null ? zone.getName() : null)
                .posteType(zone != null && zone.getPosteType() != null ?
                        zone.getPosteType().name() : null)
                // Line
                .lineId(line != null ? line.getId() : null)
                .lineCode(line != null ? line.getCode() : null)
                .lineName(line != null ? line.getName() : null)
                // Phase
                .phaseId(phase != null ? phase.getId() : null)
                .phaseCode(phase != null ? phase.getCode() : null)
                .phaseName(phase != null ? phase.getName() : null)
                // Secteur
                .secteurId(secteur != null ? secteur.getId() : null)
                .secteurCode(secteur != null ? secteur.getCode() : null)
                // Creator
                .createdById(entity.getCreatedBy() != null ?
                        entity.getCreatedBy().getId() : null)
                .createdByUsername(entity.getCreatedBy() != null ?
                        entity.getCreatedBy().getUsername() : null)
                // Planning
                .plannedDate(entity.getPlannedDate())
                .plannedWeekStart(entity.getPlannedWeekStart())
                .plannedWeekEnd(entity.getPlannedWeekEnd())
                // Dates
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                // Comments
                .comments(entity.getComments())
                .prepComments(entity.getPrepComments())
                .reviewComments(entity.getReviewComments())
                // Tool verification
                .toolsVerified(entity.getToolsVerified())
                .toolsVerifiedAt(entity.getToolsVerifiedAt())
                .toolsVerifiedByUsername(entity.getToolsVerifiedBy() != null ?
                        entity.getToolsVerifiedBy().getUsername() : null)
                // Results
                .resultsCount(resultsCount)
                .conformCount((int) conformCount)
                .nonConformCount(nonConformCount)
                .conformityRate(conformityRate)
                // AI
                .riskScore(prediction != null ? prediction.getRiskScore() : null)
                .riskLevel(prediction != null ? prediction.getRiskLevel() : null)
                .confidence(prediction != null ? prediction.getConfidence() : null)
                // Assignments
                .assignments(entity.getAssignments() != null ?
                        entity.getAssignments().stream()
                        .map(assignmentMapper::toResponseDTO)
                        .toList() : Collections.emptyList())
                // Per-poste sub-statuses (2026-04 line-ticket model)
                .posteStatuses(posteStatusDtos)
                .posteTotal(posteTotal)
                .posteDone(posteDone)
                .posteConforme(posteConforme)
                .posteNonConforme(posteNonConforme)
                // Timestamps
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Map one {@link ValidationPosteStatus} row to its DTO. */
    public PosteStatusDTO toPosteStatusDTO(ValidationPosteStatus entity) {
        if (entity == null) return null;
        ValidationZone zone = entity.getZone();
        User validatedBy = entity.getValidatedBy();
        return PosteStatusDTO.builder()
                .id(entity.getId())
                .validationId(entity.getValidation() != null ?
                        entity.getValidation().getId() : null)
                .zoneId(zone != null ? zone.getId() : null)
                .zoneName(zone != null ? zone.getName() : null)
                .posteType(zone != null && zone.getPosteType() != null ?
                        zone.getPosteType().name() : null)
                .orderInLine(entity.getOrderInLine())
                .status(entity.getStatus())
                .validatedById(validatedBy != null ? validatedBy.getId() : null)
                .validatedByUsername(validatedBy != null ? validatedBy.getUsername() : null)
                .validatedAt(entity.getValidatedAt())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}