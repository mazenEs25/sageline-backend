package com.pfe.sageline.service;

import com.pfe.sageline.dtos.ValidationRequestDTO;
import com.pfe.sageline.dtos.ValidationResponseDTO;
import com.pfe.sageline.entity.NonConformityPrediction;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.entity.ValidationStatus;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.ValidationMapper;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.repository.ValidationZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ValidationService {

    private final ValidationRepository validationRepository;
    private final ValidationZoneRepository validationZoneRepository;
    private final ValidationMapper validationMapper;
    private final KPIService kpiService;
    private final AIPredictionService aiPredictionService;  // ← NOUVEAU

    /**
     * Lancer une nouvelle validation avec prédiction IA
     */
    public ValidationResponseDTO startValidation(ValidationRequestDTO requestDTO) {
        log.info("Starting new validation for zone {}", requestDTO.getValidationZoneId());

        // Vérifier que la zone existe
        ValidationZone zone = validationZoneRepository.findById(requestDTO.getValidationZoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation zone not found with id: " + requestDTO.getValidationZoneId()));

        Validation validation = validationMapper.toEntity(requestDTO);
        validation.setValidationZone(zone);
        validation.setStatus(ValidationStatus.EN_COURS);
        validation.setStartDate(LocalDateTime.now());

        Validation savedValidation = validationRepository.save(validation);

        // ✅ APPELER L'IA POUR PRÉDICTION INITIALE (optionnel)
        // On peut prédire dès le début même sans résultats
        try {
            NonConformityPrediction prediction = aiPredictionService.predictNonConformity(savedValidation);
            log.info("Initial AI prediction for validation {}: Risk={}, Level={}",
                    savedValidation.getId(), prediction.getRiskScore(), prediction.getRiskLevel());
        } catch (Exception e) {
            log.warn("Could not get initial AI prediction: {}", e.getMessage());
        }

        return validationMapper.toResponseDTO(savedValidation);
    }

    /**
     * Ajouter des résultats et mettre à jour la prédiction IA
     */
    public ValidationResponseDTO updateValidationWithResults(Long validationId) {
        log.info("Updating AI prediction for validation {}", validationId);

        Validation validation = validationRepository.findByIdWithResults(validationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation not found with id: " + validationId));

        // ✅ APPELER L'IA APRÈS AJOUT DE RÉSULTATS
        try {
            NonConformityPrediction prediction = aiPredictionService.predictNonConformity(validation);

            // Si risque CRITIQUE, logger une alerte
            if ("CRITIQUE".equals(prediction.getRiskLevel())) {
                log.warn("⚠️ CRITICAL RISK detected for validation {}: Score={}",
                        validationId, prediction.getRiskScore());
            }

        } catch (Exception e) {
            log.error("Error updating AI prediction: {}", e.getMessage());
        }

        return validationMapper.toResponseDTO(validation);
    }

    /**
     * Clôturer une validation avec recalcul des KPIs
     */
    public ValidationResponseDTO closeValidation(Long id, ValidationStatus finalStatus, String comments) {
        log.info("Closing validation {} with status {}", id, finalStatus);

        Validation validation = validationRepository.findByIdWithResults(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation not found with id: " + id));

        if (validation.getStatus() != ValidationStatus.EN_COURS) {
            throw new RuntimeException("Validation is already closed");
        }

        validation.setStatus(finalStatus);
        validation.setEndDate(LocalDateTime.now());

        if (comments != null && !comments.isEmpty()) {
            validation.setComments(comments);
        }

        Validation updatedValidation = validationRepository.save(validation);

        // ✅ RECALCULER LES KPIs AUTOMATIQUEMENT
        Long lineId = validation.getValidationZone().getProductionLine().getId();
        try {
            kpiService.recalculateKPIsForLine(lineId);
            log.info("KPIs recalculated for line {}", lineId);
        } catch (Exception e) {
            log.error("Error recalculating KPIs: {}", e.getMessage());
        }

        return validationMapper.toResponseDTO(updatedValidation);
    }

    // ========== MÉTHODES EXISTANTES ==========

    @Transactional(readOnly = true)
    public ValidationResponseDTO getValidationById(Long id) {
        Validation validation = validationRepository.findByIdWithResults(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation not found with id: " + id));
        return validationMapper.toResponseDTO(validation);
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getAllValidations() {
        return validationRepository.findAll().stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getValidationsByZone(Long zoneId) {
        return validationRepository.findByValidationZoneId(zoneId).stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getValidationsByStatus(ValidationStatus status) {
        return validationRepository.findByStatus(status).stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getActiveValidations() {
        return validationRepository.findActiveValidations().stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getValidationsByProductionLine(Long lineId) {
        return validationRepository.findByProductionLineId(lineId).stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResponseDTO> getValidationsByDateRange(LocalDateTime start, LocalDateTime end) {
        return validationRepository.findByDateRange(start, end).stream()
                .map(validationMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public ValidationResponseDTO createValidation(ValidationRequestDTO requestDTO) {
        return startValidation(requestDTO);
    }

    public ValidationResponseDTO updateValidation(Long id, ValidationRequestDTO requestDTO) {
        Validation validation = validationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation not found with id: " + id));

        if (requestDTO.getComments() != null) {
            validation.setComments(requestDTO.getComments());
        }

        Validation updatedValidation = validationRepository.save(validation);
        return validationMapper.toResponseDTO(updatedValidation);
    }

    public void deleteValidation(Long id) {
        if (!validationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Validation not found with id: " + id);
        }
        validationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ValidationResponseDTO getValidationWithResults(Long id) {
        return getValidationById(id);
    }
}