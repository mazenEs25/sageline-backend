package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.PosteMeasureCatalogBatchRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogUpdateRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.*;
import com.pfe.sageline.mappers.PosteMeasureCatalogMapper;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PosteCatalogServiceImpl implements PosteCatalogService {

    private final PosteMeasureCatalogRepository repository;
    private final PosteMeasureCatalogMapper mapper;

    @Override
    public List<PosteMeasureCatalogResponse> listAll(PosteType filter, boolean includeInactive) {
        List<PosteMeasureCatalog> entities;
        if (filter == null && !includeInactive) {
            entities = repository.findByActive(true);
        } else if (filter == null && includeInactive) {
            entities = repository.findAll();
        } else if (filter != null && !includeInactive) {
            entities = repository.findByPosteTypeAndActiveOrderByDisplayOrder(filter, true);
        } else {
            entities = repository.findByPosteTypeOrderByDisplayOrder(filter);
        }
        return mapper.toResponseList(entities);
    }

    @Override
    public List<PosteMeasureCatalogResponse> getByPosteType(PosteType posteType, boolean includeInactive) {
        List<PosteMeasureCatalog> entities;
        if (!includeInactive) {
            entities = repository.findByPosteTypeAndActiveOrderByDisplayOrder(posteType, true);
        } else {
            entities = repository.findByPosteTypeOrderByDisplayOrder(posteType);
        }
        return mapper.toResponseList(entities);
    }

    @Override
    public List<PosteMeasureCatalogResponse> getMeasuresByPosteType(PosteType posteType) {
        List<PosteMeasureCatalog> entities = repository.findByPosteTypeAndActiveOrderByDisplayOrder(posteType, true);
        return mapper.toResponseList(entities);
    }

    @Override
    public List<PosteType> listPostesWithActive() {
        return repository.findDistinctActivePosteTypes();
    }

    @Override
    public PosteMeasureCatalogResponse getById(Long id) {
        PosteMeasureCatalog entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Measure template not found with id: " + id));
        return mapper.toResponse(entity);
    }

    @Override
    public PosteMeasureCatalogResponse create(PosteMeasureCatalogRequest request) {
        requireValidBounds(request.defaultLowerBound(), request.defaultUpperBound());

        if (repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(request.posteType(), request.measureCode())) {
            throw new DuplicateCatalogTemplateException(List.of(request.measureCode()));
        }

        PosteMeasureCatalog entity = PosteMeasureCatalog.builder()
            .posteType(request.posteType())
            .measureCode(request.measureCode())
            .measureLabel(request.measureLabel())
            .category(request.category())
            .defaultUnit(request.defaultUnit())
            .defaultLowerBound(request.defaultLowerBound())
            .defaultUpperBound(request.defaultUpperBound())
            .mandatory(request.mandatory())
            .displayOrder(request.displayOrder())
            .active(true)
            .antenna(request.antenna())
            .frequencyMhz(request.frequencyMhz())
            .modulationScheme(request.modulationScheme())
            .build();

        PosteMeasureCatalog saved = repository.save(entity);
        log.info("Created measure template: {} for poste type: {}", request.measureCode(), request.posteType());
        return mapper.toResponse(saved);
    }

    @Override
    public PosteMeasureCatalogResponse update(Long id, PosteMeasureCatalogUpdateRequest request) {
        PosteMeasureCatalog entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Measure template not found with id: " + id));

        if (request.measureLabel() != null) {
            entity.setMeasureLabel(request.measureLabel());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.defaultUnit() != null) {
            entity.setDefaultUnit(request.defaultUnit());
        }
        if (request.defaultLowerBound() != null) {
            entity.setDefaultLowerBound(request.defaultLowerBound());
        }
        if (request.defaultUpperBound() != null) {
            entity.setDefaultUpperBound(request.defaultUpperBound());
        }
        if (request.mandatory() != null) {
            entity.setMandatory(request.mandatory());
        }
        if (request.displayOrder() != null) {
            entity.setDisplayOrder(request.displayOrder());
        }
        if (request.antenna() != null) {
            entity.setAntenna(request.antenna());
        }
        if (request.frequencyMhz() != null) {
            entity.setFrequencyMhz(request.frequencyMhz());
        }
        if (request.modulationScheme() != null) {
            entity.setModulationScheme(request.modulationScheme());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        if (request.defaultLowerBound() != null || request.defaultUpperBound() != null) {
            requireValidBounds(entity.getDefaultLowerBound(), entity.getDefaultUpperBound());
        }

        PosteMeasureCatalog saved = repository.save(entity);
        log.info("Updated measure template with id: {}", id);
        return mapper.toResponse(saved);
    }

    @Override
    public void softDelete(Long id) {
        PosteMeasureCatalog entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Measure template not found with id: " + id));

        if (!entity.getActive()) {
            return;
        }

        entity.setActive(false);
        repository.save(entity);
        log.info("Soft deleted measure template with id: {}", id);
    }

    @Override
    public List<PosteMeasureCatalogResponse> batchCreate(PosteMeasureCatalogBatchRequest request) {
        List<BatchValidationException.BatchValidationError> errors = new ArrayList<>();

        // Step 1: Validate each item's bounds and check for in-batch duplicates
        Set<String> seenCodes = new HashSet<>();
        for (int i = 0; i < request.items().size(); i++) {
            PosteMeasureCatalogBatchRequest.BatchItem item = request.items().get(i);

            // Check bounds
            if (item.defaultLowerBound() >= item.defaultUpperBound()) {
                errors.add(new BatchValidationException.BatchValidationError(
                    i, "bounds", "BOUNDS_VIOLATION",
                    "Lower bound must be less than upper bound"
                ));
            }

            // Check for duplicates within the batch
            if (!seenCodes.add(item.measureCode())) {
                errors.add(new BatchValidationException.BatchValidationError(
                    i, "measureCode", "DUPLICATE_CODE",
                    "Duplicate measure code in batch"
                ));
            }
        }

        if (!errors.isEmpty()) {
            throw new BatchValidationException(errors);
        }

        // Step 2: Check for conflicts with existing database records
        List<String> codes = request.items().stream()
            .map(PosteMeasureCatalogBatchRequest.BatchItem::measureCode)
            .collect(Collectors.toList());

        List<PosteMeasureCatalog> existing = repository
            .findAllByPosteTypeAndMeasureCodeInAndActiveTrue(request.posteType(), codes);

        if (!existing.isEmpty()) {
            List<String> conflictingCodes = existing.stream()
                .map(PosteMeasureCatalog::getMeasureCode)
                .collect(Collectors.toList());
            throw new DuplicateCatalogTemplateException(conflictingCodes);
        }

        // Step 3: Create and persist all items (atomically)
        List<PosteMeasureCatalog> entities = new ArrayList<>();
        for (PosteMeasureCatalogBatchRequest.BatchItem item : request.items()) {
            PosteMeasureCatalog entity = PosteMeasureCatalog.builder()
                .posteType(request.posteType())
                .measureCode(item.measureCode())
                .measureLabel(item.measureLabel())
                .category(item.category())
                .defaultUnit(item.defaultUnit())
                .defaultLowerBound(item.defaultLowerBound())
                .defaultUpperBound(item.defaultUpperBound())
                .mandatory(item.mandatory())
                .displayOrder(item.displayOrder())
                .active(true)
                .antenna(item.antenna())
                .frequencyMhz(item.frequencyMhz())
                .modulationScheme(item.modulationScheme())
                .build();
            entities.add(entity);
        }

        List<PosteMeasureCatalog> saved = repository.saveAll(entities);
        log.info("Batch created {} measure templates for poste type: {}", saved.size(), request.posteType());
        return mapper.toResponseList(saved);
    }

    private static void requireValidBounds(Double lower, Double upper) {
        if (lower >= upper) {
            throw new BoundsViolationException("Lower bound must be strictly less than upper bound");
        }
    }
}
