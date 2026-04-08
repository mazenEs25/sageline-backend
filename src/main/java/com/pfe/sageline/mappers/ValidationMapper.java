package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.ValidationRequestDTO;
import com.pfe.sageline.dtos.ValidationResponseDTO;
import com.pfe.sageline.entity.Validation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ValidationMapper {
    
    @Autowired
    private ValidationResultMapper resultMapper;
    
    public ValidationResponseDTO toResponseDTO(Validation validation) {
        if (validation == null) {
            return null;
        }
        
        ValidationResponseDTO dto = new ValidationResponseDTO();
        dto.setId(validation.getId());
        dto.setStartDate(validation.getStartDate());
        dto.setEndDate(validation.getEndDate());
        dto.setStatus(validation.getStatus());
        dto.setComments(validation.getComments());
        
        if (validation.getValidationZone() != null) {
            dto.setValidationZoneId(validation.getValidationZone().getId());
            dto.setValidationZoneName(validation.getValidationZone().getName());
            
            if (validation.getValidationZone().getProductionLine() != null) {
                dto.setProductionLineName(validation.getValidationZone().getProductionLine().getName());
            }
        }
        
        if (validation.getValidationResults() != null) {
            dto.setResults(validation.getValidationResults().stream()
                    .map(resultMapper::toResponseDTO)
                    .collect(Collectors.toList()));
        }
        
        if (validation.getNonConformityPrediction() != null) {
            dto.setRiskScore(validation.getNonConformityPrediction().getRiskScore());
            dto.setRiskLevel(validation.getNonConformityPrediction().getRiskLevel());
        }
        
        return dto;
    }
    
    public Validation toEntity(ValidationRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Validation validation = new Validation();
        validation.setComments(dto.getComments());
        
        return validation;
    }
}
