package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.ValidationResultRequestDTO;
import com.pfe.sageline.dtos.ValidationResultResponseDTO;
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
