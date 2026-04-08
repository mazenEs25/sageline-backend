package com.pfe.sageline.mappers;


import com.pfe.sageline.dtos.ProductionLineRequestDTO;
import com.pfe.sageline.dtos.ProductionLineResponseDTO;
import com.pfe.sageline.entity.ProductionLine;
import org.springframework.stereotype.Component;

@Component
public class ProductionLineMapper {
    
    public ProductionLineResponseDTO toResponseDTO(ProductionLine line) {
        if (line == null) {
            return null;
        }
        
        ProductionLineResponseDTO dto = new ProductionLineResponseDTO();
        dto.setId(line.getId());
        dto.setCode(line.getCode());
        dto.setName(line.getName());
        dto.setActive(line.getActive());
        dto.setCreatedAt(line.getCreatedAt());
        dto.setUpdatedAt(line.getUpdatedAt());
        
        if (line.getValidationZones() != null) {
            dto.setZonesCount(line.getValidationZones().size());
        } else {
            dto.setZonesCount(0);
        }
        
        return dto;
    }
    
    public ProductionLine toEntity(ProductionLineRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ProductionLine line = new ProductionLine();
        line.setCode(dto.getCode());
        line.setName(dto.getName());
        line.setActive(dto.getActive() != null ? dto.getActive() : true);
        
        return line;
    }
    
    public void updateEntityFromDTO(ProductionLineRequestDTO dto, ProductionLine line) {
        if (dto == null || line == null) {
            return;
        }
        
        line.setCode(dto.getCode());
        line.setName(dto.getName());
        if (dto.getActive() != null) {
            line.setActive(dto.getActive());
        }
    }
}
