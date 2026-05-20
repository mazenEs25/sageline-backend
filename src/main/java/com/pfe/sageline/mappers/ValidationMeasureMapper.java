package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ValidationMeasureMapper {

    private final UserRepository userRepository;

    public ValidationMeasureResponse toResponse(ValidationMeasure entity) {
        String enteredByUsername = null;
        if (entity.getEnteredBy() != null) {
            enteredByUsername = userRepository.findById(entity.getEnteredBy())
                    .map(u -> u.getUsername())
                    .orElse(null);
        }
        return toResponse(entity, enteredByUsername);
    }

    /**
     * Batch-friendly variant: resolves every distinct {@code enteredBy} id with a single
     * {@code findAllById} call, then maps each entity using the prebuilt id→username map.
     * Use this for {@code listByValidation}, {@code instantiateFromCatalog}, and {@code batchCreate}
     * to avoid the N+1 lookup pattern.
     */
    public List<ValidationMeasureResponse> toResponseList(List<ValidationMeasure> entities) {
        Set<Long> userIds = entities.stream()
                .map(ValidationMeasure::getEnteredBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> usernameById = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds)
                    .forEach(u -> usernameById.put(u.getId(), u.getUsername()));
        }

        return entities.stream()
                .map(e -> toResponse(e, e.getEnteredBy() != null ? usernameById.get(e.getEnteredBy()) : null))
                .toList();
    }

    private ValidationMeasureResponse toResponse(ValidationMeasure entity, String enteredByUsername) {
        return new ValidationMeasureResponse(
                entity.getId(),
                entity.getValidation().getId(),
                entity.getPosteStatus() != null ? entity.getPosteStatus().getId() : null,
                entity.getCatalogTemplate() != null ? entity.getCatalogTemplate().getId() : null,
                entity.getMeasureCode(),
                entity.getMeasureLabel(),
                entity.getCategory(),
                entity.getUnit(),
                entity.getLowerBound(),
                entity.getUpperBound(),
                entity.getMeasuredValue(),
                entity.getStatus(),
                entity.getDeviationPct(),
                entity.getAntenna(),
                entity.getFrequencyMhz(),
                entity.getModulationScheme(),
                entity.getSourceLogFile(),
                entity.getEnteredBy(),
                enteredByUsername,
                entity.getMeasuredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
