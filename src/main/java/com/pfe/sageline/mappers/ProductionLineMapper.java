package com.pfe.sageline.mappers;


import com.pfe.sageline.dtos.request.ProductionLineRequestDTO;
import com.pfe.sageline.dtos.response.ProductionLineResponseDTO;
import com.pfe.sageline.entity.Phase;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Secteur;
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
        dto.setLineNumber(line.getLineNumber());

        if (line.getValidationZones() != null) {
            dto.setZonesCount(line.getValidationZones().size());
        } else {
            dto.setZonesCount(0);
        }

        Phase phase = line.getPhase();
        if (phase != null) {
            dto.setPhaseId(phase.getId());
            dto.setPhaseCode(phase.getCode());
            dto.setPhaseName(phase.getName());

            Secteur secteur = phase.getSecteur();
            if (secteur != null) {
                dto.setSecteurId(secteur.getId());
                dto.setSecteurCode(secteur.getCode());
                dto.setSecteurName(secteur.getName());
            }
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
        line.setLineNumber(dto.getLineNumber());
        if (dto.getPhaseId() != null) {
            Phase phase = new Phase();
            phase.setId(dto.getPhaseId());
            line.setPhase(phase);
        }

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
        if (dto.getLineNumber() != null) {
            line.setLineNumber(dto.getLineNumber());
        }
        if (dto.getPhaseId() != null) {
            Phase phase = new Phase();
            phase.setId(dto.getPhaseId());
            line.setPhase(phase);
        }
    }
}
