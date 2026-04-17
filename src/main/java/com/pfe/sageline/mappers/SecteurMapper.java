package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.SecteurRequestDTO;
import com.pfe.sageline.dtos.response.SecteurResponseDTO;
import com.pfe.sageline.entity.Secteur;
import org.springframework.stereotype.Component;
import java.util.Collections;

@Component
public class SecteurMapper {

    public Secteur toEntity(SecteurRequestDTO dto) {
        return Secteur.builder()
                .code(dto.getCode().toUpperCase())
                .name(dto.getName())
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
    }

    public SecteurResponseDTO toResponseDTO(Secteur entity) {
        return toResponseDTO(entity, false);
    }

    public SecteurResponseDTO toResponseDTO(Secteur entity, boolean includePhases) {
        SecteurResponseDTO.SecteurResponseDTOBuilder builder = SecteurResponseDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.getActive())
                .phaseCount(entity.getPhases() != null ? entity.getPhases().size() : 0)
                .lineCount(entity.getPhases() != null ?
                        entity.getPhases().stream()
                        .mapToInt(p -> p.getProductionLines() != null ?
                                p.getProductionLines().size() : 0)
                        .sum() : 0)
                .userCount(entity.getUsers() != null ? entity.getUsers().size() : 0)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (includePhases && entity.getPhases() != null) {
            PhaseMapper phaseMapper = new PhaseMapper();
            builder.phases(entity.getPhases().stream()
                    .map(phaseMapper::toResponseDTO)
                    .toList());
        } else {
            builder.phases(Collections.emptyList());
        }

        return builder.build();
    }

    public void updateEntity(Secteur entity, SecteurRequestDTO dto) {
        entity.setCode(dto.getCode().toUpperCase());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        if (dto.getActive() != null) {
            entity.setActive(dto.getActive());
        }
    }
}