package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.ValidationZoneRequestDTO;
import com.pfe.sageline.dtos.ValidationZoneResponseDTO;
import com.pfe.sageline.entity.ValidationZone;
import org.springframework.stereotype.Component;

@Component
public class ValidationZoneMapper {
    
    public ValidationZoneResponseDTO toResponseDTO(ValidationZone zone) {
        if (zone == null) {
            return null;
        }
        
        ValidationZoneResponseDTO dto = new ValidationZoneResponseDTO();
        dto.setId(zone.getId());
        dto.setName(zone.getName());
        dto.setDescription(zone.getDescription());
        dto.setCreatedAt(zone.getCreatedAt());
        dto.setUpdatedAt(zone.getUpdatedAt());
        
        if (zone.getProductionLine() != null) {
            dto.setProductionLineId(zone.getProductionLine().getId());
            dto.setProductionLineName(zone.getProductionLine().getName());
        }
        
        if (zone.getValidations() != null) {
            dto.setValidationsCount(zone.getValidations().size());
        } else {
            dto.setValidationsCount(0);
        }
        
        return dto;
    }
    
    public ValidationZone toEntity(ValidationZoneRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ValidationZone zone = new ValidationZone();
        zone.setName(dto.getName());
        zone.setDescription(dto.getDescription());
        
        return zone;
    }
    
    public void updateEntityFromDTO(ValidationZoneRequestDTO dto, ValidationZone zone) {
        if (dto == null || zone == null) {
            return;
        }
        
        zone.setName(dto.getName());
        zone.setDescription(dto.getDescription());
    }
}
