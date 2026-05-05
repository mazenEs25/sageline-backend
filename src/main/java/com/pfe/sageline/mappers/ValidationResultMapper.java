package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.ValidationResultRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResultResponseDTO;
import com.pfe.sageline.entity.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class ValidationResultMapper {
    
    public ValidationResultResponseDTO toResponseDTO(ValidationResult result) {
        if (result == null) {
            return null;
        }
        
        ValidationResultResponseDTO dto = new ValidationResultResponseDTO();
        dto.setId(result.getId());
        // Expose the parent validation's id so the frontend can filter / export per-ticket
        if (result.getValidation() != null) {
            dto.setValidationId(result.getValidation().getId());
        }
        // Expose the per-poste link (2026-04 line-ticket model) so the UI
        // can group results by poste and show per-poste counts.
        if (result.getZone() != null) {
            dto.setZoneId(result.getZone().getId());
            dto.setZoneName(result.getZone().getName());
        }
        dto.setParameter(result.getParameter());
        dto.setMeasuredValue(result.getMeasuredValue());
        dto.setExpectedValue(result.getExpectedValue());
        dto.setConform(result.getConform());
        dto.setCreatedAt(result.getCreatedAt());

        return dto;
    }
    
    public ValidationResult toEntity(ValidationResultRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ValidationResult result = new ValidationResult();
        result.setParameter(dto.getParameter());
        result.setMeasuredValue(dto.getMeasuredValue());
        result.setExpectedValue(dto.getExpectedValue());
        
        // Calcul automatique de la conformité (tolérance de 5%)
        double tolerance = 0.05;
        double deviation = Math.abs(dto.getMeasuredValue() - dto.getExpectedValue()) / dto.getExpectedValue();
        result.setConform(deviation <= tolerance);
        
        return result;
    }
    public void updateEntityFromDTO(
            ValidationResultRequestDTO dto,
            ValidationResult result) {

        if (dto == null || result == null) {
            return;
        }

        result.setParameter(dto.getParameter());
        result.setMeasuredValue(dto.getMeasuredValue());
        result.setExpectedValue(dto.getExpectedValue());

        calculateConformity(result);
    }
    private void calculateConformity(ValidationResult result) {
        if (result.getExpectedValue() == null || result.getExpectedValue() == 0) {
            result.setConform(false);
            return;
        }

        double tolerance = 0.05; // 5%
        double deviation = Math.abs(
                result.getMeasuredValue() - result.getExpectedValue()
        ) / result.getExpectedValue();

        result.setConform(deviation <= tolerance);
    }

}
