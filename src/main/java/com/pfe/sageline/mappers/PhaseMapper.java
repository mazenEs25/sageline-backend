package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.PhaseRequestDTO;
import com.pfe.sageline.dtos.response.PhaseResponseDTO;
import com.pfe.sageline.entity.Phase;
import com.pfe.sageline.entity.Secteur;
import org.springframework.stereotype.Component;

@Component
public class PhaseMapper {

    public Phase toEntity(PhaseRequestDTO dto, Secteur secteur) {
        return Phase.builder()
                .code(dto.getCode().toUpperCase())
                .name(dto.getName())
                .secteur(secteur)
                .orderIndex(dto.getOrderIndex())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
    }

    public PhaseResponseDTO toResponseDTO(Phase entity) {
        return PhaseResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .secteurId(entity.getSecteur() != null ? entity.getSecteur().getId() : null)
                .secteurCode(entity.getSecteur() != null ? entity.getSecteur().getCode() : null)
                .secteurName(entity.getSecteur() != null ? entity.getSecteur().getName() : null)
                .orderIndex(entity.getOrderIndex())
                .active(entity.getActive())
                .lineCount(entity.getProductionLines() != null ?
                        entity.getProductionLines().size() : 0)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void updateEntity(Phase entity, PhaseRequestDTO dto) {
        entity.setCode(dto.getCode().toUpperCase());
        entity.setName(dto.getName());
        entity.setOrderIndex(dto.getOrderIndex());
        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }
    }
}