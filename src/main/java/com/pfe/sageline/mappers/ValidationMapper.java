package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.TicketCreateRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResponseDTO;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.TicketStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Collections;

@Component
public class ValidationMapper {

    @Autowired
    private ValidationAssignmentMapper assignmentMapper;

    public Validation toEntity(TicketCreateRequestDTO dto, ValidationZone zone, User createdBy, String ticketCode) {
        return Validation.builder()
                .ticketCode(ticketCode)
                .status(TicketStatus.PLANIFIE)
                .validationZone(zone)
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
        ValidationZone zone = entity.getValidationZone();
        ProductionLine line = zone != null ? zone.getProductionLine() : null;
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
                // Timestamps
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}