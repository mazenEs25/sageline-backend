package com.pfe.sageline.service;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.BatchMeasureValidationException;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.ValidationMeasureMapper;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ValidationMeasureServiceImpl implements ValidationMeasureService {

    private final ValidationMeasureRepository measureRepository;
    private final ValidationRepository validationRepository;
    private final PosteMeasureCatalogRepository catalogRepository;
    private final MeasureDeviationCalculator deviationCalculator;
    private final MeasureEditabilityGuard editabilityGuard;
    private final ValidationMeasureMapper mapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public List<ValidationMeasureResponse> listByValidation(Long validationId) {
        if (!validationRepository.existsById(validationId)) {
            throw new ResourceNotFoundException("Validation", "id", validationId);
        }
        return mapper.toResponseList(
                measureRepository.findAllByValidationIdFetchTemplate(validationId));
    }

    @Override
    public ValidationMeasureResponse create(Long validationId, CreateMeasureRequest req) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        PosteMeasureCatalog template = null;
        String measureCode = req.getMeasureCode();
        String measureLabel = req.getMeasureLabel();
        com.pfe.sageline.enums.MeasureCategory category = req.getCategory();
        String unit = req.getUnit();
        Double lowerBound = req.getLowerBound();
        Double upperBound = req.getUpperBound();
        String antenna = req.getAntenna();
        Integer frequencyMhz = req.getFrequencyMhz();
        String modulationScheme = req.getModulationScheme();

        if (req.getTemplateId() != null) {
            template = catalogRepository.findById(req.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("PosteMeasureCatalog", "id", req.getTemplateId()));
            PosteType ticketPosteType = ticket.getValidationZone() != null
                    ? ticket.getValidationZone().getPosteType() : null;
            if (ticketPosteType == null || template.getPosteType() != ticketPosteType) {
                throw new ValidationException("Template poste type does not match ticket zone poste type");
            }
            measureCode = template.getMeasureCode();
            measureLabel = template.getMeasureLabel();
            category = template.getCategory();
            unit = template.getDefaultUnit();
            lowerBound = template.getDefaultLowerBound();
            upperBound = template.getDefaultUpperBound();
            antenna = template.getAntenna();
            frequencyMhz = template.getFrequencyMhz();
            modulationScheme = template.getModulationScheme();
        }

        MeasureStatus status = deviationCalculator.computeStatus(req.getMeasuredValue(), lowerBound, upperBound);
        Double deviationPct = deviationCalculator.computeDeviationPct(req.getMeasuredValue(), lowerBound, upperBound);

        ValidationMeasure entity = ValidationMeasure.builder()
                .validation(ticket)
                .catalogTemplate(template)
                .measureCode(measureCode)
                .measureLabel(measureLabel)
                .category(category)
                .unit(unit)
                .lowerBound(lowerBound)
                .upperBound(upperBound)
                .measuredValue(req.getMeasuredValue())
                .status(status)
                .deviationPct(deviationPct)
                .antenna(antenna)
                .frequencyMhz(frequencyMhz)
                .modulationScheme(modulationScheme)
                .enteredBy(securityUtils.getCurrentUserId())
                .measuredAt(Instant.now())
                .build();

        ValidationMeasure saved = measureRepository.save(entity);
        return mapper.toResponse(saved);
    }

    @Override
    public List<ValidationMeasureResponse> instantiateFromCatalog(Long validationId) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        PosteType posteType = ticket.getValidationZone() != null
                ? ticket.getValidationZone().getPosteType() : null;
        if (posteType == null) {
            return List.of();
        }

        List<PosteMeasureCatalog> templates =
                catalogRepository.findByPosteTypeAndActiveOrderByDisplayOrder(posteType, true);
        if (templates.isEmpty()) {
            return List.of();
        }

        List<Long> alreadyPresent = measureRepository.findCatalogTemplateIdsPresentOnTicket(
                validationId,
                templates.stream().map(PosteMeasureCatalog::getId).toList());

        Set<Long> presentSet = new HashSet<>(alreadyPresent);
        Long operatorId = securityUtils.getCurrentUserId();
        Instant now = Instant.now();

        List<ValidationMeasure> toCreate = new ArrayList<>();
        for (PosteMeasureCatalog t : templates) {
            if (presentSet.contains(t.getId())) {
                continue;
            }
            toCreate.add(ValidationMeasure.builder()
                    .validation(ticket)
                    .catalogTemplate(t)
                    .measureCode(t.getMeasureCode())
                    .measureLabel(t.getMeasureLabel())
                    .category(t.getCategory())
                    .unit(t.getDefaultUnit())
                    .lowerBound(t.getDefaultLowerBound())
                    .upperBound(t.getDefaultUpperBound())
                    .measuredValue(null)
                    .status(MeasureStatus.NOT_EXECUTED)
                    .deviationPct(null)
                    .antenna(t.getAntenna())
                    .frequencyMhz(t.getFrequencyMhz())
                    .modulationScheme(t.getModulationScheme())
                    .enteredBy(operatorId)
                    .measuredAt(now)
                    .build());
        }

        return mapper.toResponseList(measureRepository.saveAll(toCreate));
    }

    @Override
    public ValidationMeasureResponse update(Long validationId, Long measureId, UpdateMeasureRequest req) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        ValidationMeasure m = measureRepository.findByIdAndValidationId(measureId, validationId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationMeasure", "id", measureId));

        // null measuredValue in request is treated as explicit clear (last-writer-wins, no JsonNullable in Phase 002)
        m.setMeasuredValue(req.getMeasuredValue());

        if (req.getLowerBound() != null) m.setLowerBound(req.getLowerBound());
        if (req.getUpperBound() != null) m.setUpperBound(req.getUpperBound());
        if (req.getUnit() != null) m.setUnit(req.getUnit());
        if (req.getAntenna() != null) m.setAntenna(req.getAntenna());
        if (req.getFrequencyMhz() != null) m.setFrequencyMhz(req.getFrequencyMhz());
        if (req.getModulationScheme() != null) m.setModulationScheme(req.getModulationScheme());

        m.setStatus(deviationCalculator.computeStatus(m.getMeasuredValue(), m.getLowerBound(), m.getUpperBound()));
        m.setDeviationPct(deviationCalculator.computeDeviationPct(m.getMeasuredValue(), m.getLowerBound(), m.getUpperBound()));
        m.setEnteredBy(securityUtils.getCurrentUserId());
        m.setMeasuredAt(Instant.now());

        return mapper.toResponse(measureRepository.save(m));
    }

    @Override
    public void delete(Long validationId, Long measureId) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        ValidationMeasure m = measureRepository.findByIdAndValidationId(measureId, validationId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationMeasure", "id", measureId));
        measureRepository.delete(m);
    }

    @Override
    public List<ValidationMeasureResponse> batchCreate(Long validationId, BatchCreateMeasureRequest req) {
        Validation ticket = loadTicket(validationId);

        try {
            editabilityGuard.requireEditable(ticket);
        } catch (com.pfe.sageline.exception.MeasureNotEditableException ex) {
            throw new BatchMeasureValidationException(
                    req.getMeasures().size(),
                    List.of(new BatchMeasureValidationException.FailedEntry(-1, "TICKET_NOT_EDITABLE", ex.getMessage())));
        }

        PosteType posteType = ticket.getValidationZone() != null
                ? ticket.getValidationZone().getPosteType() : null;

        List<BatchMeasureValidationException.FailedEntry> failures = new ArrayList<>();
        Set<String> batchNaturalKeys = new HashSet<>();
        List<ResolvedEntry> resolved = new ArrayList<>();

        List<CreateMeasureRequest> measures = req.getMeasures();
        for (int i = 0; i < measures.size(); i++) {
            CreateMeasureRequest entry = measures.get(i);
            PosteMeasureCatalog template = null;
            String measureCode = entry.getMeasureCode();
            String measureLabel = entry.getMeasureLabel();
            com.pfe.sageline.enums.MeasureCategory category = entry.getCategory();
            String unit = entry.getUnit();
            Double lowerBound = entry.getLowerBound();
            Double upperBound = entry.getUpperBound();
            String antenna = entry.getAntenna();
            Integer frequencyMhz = entry.getFrequencyMhz();
            String modulationScheme = entry.getModulationScheme();

            if (entry.getTemplateId() != null) {
                var optTemplate = catalogRepository.findById(entry.getTemplateId());
                if (optTemplate.isEmpty()) {
                    failures.add(new BatchMeasureValidationException.FailedEntry(
                            i, "UNKNOWN_TEMPLATE", "templateId " + entry.getTemplateId() + " not found"));
                    continue;
                }
                template = optTemplate.get();
                if (posteType != null && template.getPosteType() != posteType) {
                    failures.add(new BatchMeasureValidationException.FailedEntry(
                            i, "OWNER_MISMATCH", "Template poste type does not match ticket zone poste type"));
                    continue;
                }
                measureCode = template.getMeasureCode();
                measureLabel = template.getMeasureLabel();
                category = template.getCategory();
                unit = template.getDefaultUnit();
                lowerBound = template.getDefaultLowerBound();
                upperBound = template.getDefaultUpperBound();
                antenna = template.getAntenna();
                frequencyMhz = template.getFrequencyMhz();
                modulationScheme = template.getModulationScheme();
            }

            String naturalKey = measureCode + "|"
                    + (antenna != null ? antenna : "") + "|"
                    + (frequencyMhz != null ? frequencyMhz : -1) + "|"
                    + (modulationScheme != null ? modulationScheme : "");

            boolean duplicateInBatch = !batchNaturalKeys.add(naturalKey);
            boolean duplicateInDb = measureRepository
                    .existsByValidationIdAndMeasureCodeAndAntennaAndFrequencyMhzAndModulationScheme(
                            validationId, measureCode, antenna, frequencyMhz, modulationScheme);

            if (duplicateInBatch || duplicateInDb) {
                failures.add(new BatchMeasureValidationException.FailedEntry(
                        i, "DUPLICATE_MEASURE_CODE", "Duplicate measure code: " + measureCode));
                continue;
            }

            resolved.add(new ResolvedEntry(template, measureCode, measureLabel, category,
                    unit, lowerBound, upperBound, entry.getMeasuredValue(),
                    antenna, frequencyMhz, modulationScheme));
        }

        if (!failures.isEmpty()) {
            throw new BatchMeasureValidationException(measures.size(), failures);
        }

        Long operatorId = securityUtils.getCurrentUserId();
        Instant now = Instant.now();

        List<ValidationMeasure> entities = resolved.stream().map(r -> {
            MeasureStatus status = deviationCalculator.computeStatus(r.measuredValue, r.lowerBound, r.upperBound);
            Double dev = deviationCalculator.computeDeviationPct(r.measuredValue, r.lowerBound, r.upperBound);
            return ValidationMeasure.builder()
                    .validation(ticket)
                    .catalogTemplate(r.template)
                    .measureCode(r.measureCode)
                    .measureLabel(r.measureLabel)
                    .category(r.category)
                    .unit(r.unit)
                    .lowerBound(r.lowerBound)
                    .upperBound(r.upperBound)
                    .measuredValue(r.measuredValue)
                    .status(status)
                    .deviationPct(dev)
                    .antenna(r.antenna)
                    .frequencyMhz(r.frequencyMhz)
                    .modulationScheme(r.modulationScheme)
                    .enteredBy(operatorId)
                    .measuredAt(now)
                    .build();
        }).toList();

        return mapper.toResponseList(measureRepository.saveAll(entities));
    }

    private Validation loadTicket(Long validationId) {
        return validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation", "id", validationId));
    }

    private record ResolvedEntry(
            PosteMeasureCatalog template,
            String measureCode,
            String measureLabel,
            com.pfe.sageline.enums.MeasureCategory category,
            String unit,
            Double lowerBound,
            Double upperBound,
            Double measuredValue,
            String antenna,
            Integer frequencyMhz,
            String modulationScheme
    ) {}
}
