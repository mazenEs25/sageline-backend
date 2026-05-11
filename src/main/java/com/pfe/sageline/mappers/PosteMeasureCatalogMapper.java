package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class PosteMeasureCatalogMapper {

    public PosteMeasureCatalogResponse toResponse(PosteMeasureCatalog entity) {
        if (entity == null) {
            return null;
        }
        return new PosteMeasureCatalogResponse(
            entity.getId(),
            entity.getPosteType(),
            entity.getMeasureCode(),
            entity.getMeasureLabel(),
            entity.getCategory(),
            entity.getDefaultUnit(),
            entity.getDefaultLowerBound(),
            entity.getDefaultUpperBound(),
            entity.getMandatory(),
            entity.getDisplayOrder(),
            entity.getActive(),
            entity.getAntenna(),
            entity.getFrequencyMhz(),
            entity.getModulationScheme(),
            entity.getCreatedAt(),
            entity.getCreatedBy(),
            entity.getUpdatedAt(),
            entity.getUpdatedBy()
        );
    }

    public List<PosteMeasureCatalogResponse> toResponseList(Collection<PosteMeasureCatalog> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
            .map(this::toResponse)
            .toList();
    }
}
