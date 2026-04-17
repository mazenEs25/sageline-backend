package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.ValidationResultRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResultResponseDTO;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationResult;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.ValidationResultMapper;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.repository.ValidationResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationResultService {

    private final ValidationResultRepository validationResultRepository;
    private final ValidationRepository validationRepository;
    private final ValidationResultMapper validationResultMapper;
    private final AIPredictionService aiPredictionService;

    /**
     * Ajouter un résultat à une validation
     * La conformité est calculée automatiquement dans le mapper
     * ✅ Déclenche la prédiction IA après ajout
     */
    public ValidationResultResponseDTO addValidationResult(ValidationResultRequestDTO requestDTO) {
        log.info("Adding result to validation {}", requestDTO.getValidationId());

        // Vérifier que la validation existe et est EN_COURS
        Validation validation = validationRepository.findById(requestDTO.getValidationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation not found with id: " + requestDTO.getValidationId()));

        if (validation.getStatus() != TicketStatus.EN_COURS) {
            throw new RuntimeException("Cannot add results to a closed validation");
        }

        ValidationResult result = validationResultMapper.toEntity(requestDTO);
        result.setValidation(validation);

        ValidationResult savedResult = validationResultRepository.save(result);

        try {
            aiPredictionService.predictNonConformity(validation);
            log.info("AI prediction updated after adding result");
        } catch (Exception e) {
            log.warn("Could not update AI prediction: {}", e.getMessage());
        }

        return validationResultMapper.toResponseDTO(savedResult);
    }

    /**
     * Ajouter plusieurs résultats en une fois
     * ✅ Déclenche la prédiction IA UNE SEULE FOIS à la fin
     */
    public List<ValidationResultResponseDTO> addMultipleResults(List<ValidationResultRequestDTO> requestDTOs) {
        log.info("Adding {} results in batch", requestDTOs.size());

        if (requestDTOs.isEmpty()) {
            return List.of();
        }

        // Récupérer tous les résultats
        List<ValidationResultResponseDTO> results = requestDTOs.stream()
                .map(dto -> {
                    Validation validation = validationRepository.findById(dto.getValidationId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Validation not found with id: " + dto.getValidationId()));

                    if (validation.getStatus() != TicketStatus.EN_COURS) {
                        throw new RuntimeException("Cannot add results to a closed validation");
                    }

                    ValidationResult result = validationResultMapper.toEntity(dto);
                    result.setValidation(validation);

                    ValidationResult savedResult = validationResultRepository.save(result);
                    return validationResultMapper.toResponseDTO(savedResult);
                })
                .collect(Collectors.toList());

        if (!requestDTOs.isEmpty()) {
            Long validationId = requestDTOs.get(0).getValidationId();
            try {
                Validation v = validationRepository.findById(validationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Validation not found"));
                aiPredictionService.predictNonConformity(v);
                log.info("AI prediction updated after batch insert");
            } catch (Exception e) {
                log.warn("Could not update AI prediction: {}", e.getMessage());
            }
        }

        return results;
    }

    // ========== MÉTHODES EXISTANTES ==========


    public ValidationResultResponseDTO getValidationResultById(Long id) {
        ValidationResult result = validationResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation result not found with id: " + id));
        return validationResultMapper.toResponseDTO(result);
    }


    public List<ValidationResultResponseDTO> getResultsByValidation(Long validationId) {
        return validationResultRepository.findByValidationId(validationId).stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    public List<ValidationResultResponseDTO> getConformResults(Long validationId) {
        return validationResultRepository.findByValidationIdAndConform(validationId, true).stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResultResponseDTO> getNonConformResults(Long validationId) {
        return validationResultRepository.findByValidationIdAndConform(validationId, false).stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    public List<ValidationResultResponseDTO> getResultsByParameter(String parameter) {
        return validationResultRepository.findByParameter(parameter).stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    public Long countNonConformResults(Long validationId) {
        return validationResultRepository.countNonConformByValidation(validationId);
    }

    public void deleteValidationResult(Long id) {
        ValidationResult result = validationResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation result not found with id: " + id));

        if (result.getValidation().getStatus() != TicketStatus.EN_COURS) {
            throw new RuntimeException("Cannot delete results from a closed validation");
        }

        validationResultRepository.deleteById(id);
    }

    public ValidationResultResponseDTO createResult(ValidationResultRequestDTO requestDTO) {
        return addValidationResult(requestDTO);
    }

    public List<ValidationResultResponseDTO> createResultsBatch(List<ValidationResultRequestDTO> requests) {
        return addMultipleResults(requests);
    }

    public ValidationResultResponseDTO getResultById(Long id) {
        return getValidationResultById(id);
    }

    @Transactional(readOnly = true)
    public List<ValidationResultResponseDTO> getAllResults() {
        return validationResultRepository.findAll().stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationResultResponseDTO> getResultsByConformity(Boolean conform) {
        return validationResultRepository.findByConform(conform).stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public ValidationResultResponseDTO updateResult(Long id, ValidationResultRequestDTO requestDTO) {
        ValidationResult result = validationResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation result not found with id: " + id));

        if (result.getValidation().getStatus() != TicketStatus.EN_COURS) {
            throw new RuntimeException("Cannot update results of a closed validation");
        }

        validationResultMapper.updateEntityFromDTO(requestDTO, result);
        ValidationResult updatedResult = validationResultRepository.save(result);
        return validationResultMapper.toResponseDTO(updatedResult);
    }

    public void deleteResult(Long id) {
        deleteValidationResult(id);
    }

    public List<ValidationResultResponseDTO> getAllValidationResults() {
        return validationResultRepository.findAll().stream()
                .map(validationResultMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    public Long countNonConformByValidation(Long validationId) {
        return countNonConformResults(validationId);
    }
}