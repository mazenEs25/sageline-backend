package com.pfe.sageline.service;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.BatchUpdateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.BatchValidationMeasureResponse;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
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
import com.pfe.sageline.service.workflow.WorkflowReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ValidationMeasureServiceImpl implements ValidationMeasureService {

    private final ValidationMeasureRepository measureRepository;
    private final ValidationRepository validationRepository;
    private final PosteMeasureCatalogRepository catalogRepository;
    private final com.pfe.sageline.repository.ValidationPosteStatusRepository posteStatusRepository;
    private final MeasureDeviationCalculator deviationCalculator;
    private final MeasureEditabilityGuard editabilityGuard;
    private final ValidationMeasureMapper mapper;
    private final SecurityUtils securityUtils;
    private final SimpMessagingTemplate messagingTemplate;
    private final WorkflowReadinessService readinessService;

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
    @Transactional(readOnly = true)
    public List<ValidationMeasureResponse> listByPoste(Long validationId, Long zoneId) {
        if (!validationRepository.existsById(validationId)) {
            throw new ResourceNotFoundException("Validation", "id", validationId);
        }
        // Resolve the posteStatus on this ticket for the given zone.
        com.pfe.sageline.entity.ValidationPosteStatus ps = posteStatusRepository
                .findByValidationIdAndZoneId(validationId, zoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poste status not found for validation=" + validationId + " zone=" + zoneId));
        return mapper.toResponseList(
                measureRepository.findAllByPosteStatusIdFetchTemplate(ps.getId()));
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

        // ── Resolve "which poste does this measure belong to?" ─────────────
        // Strategy:
        //   • Mode A (templateId)  → use the FIRST posteStatus whose zone.posteType
        //                            matches the template's posteType.
        //   • Mode B (measureCode) → walk every posteStatus and pick the one whose
        //                            catalog contains this code.
        //   • Fall back to validationZone (legacy single) only if no posteStatus matches,
        //     to keep pre-line-ticket-migration data working.
        // ────────────────────────────────────────────────────────────────────
        com.pfe.sageline.entity.ValidationPosteStatus resolvedPoste = null;
        PosteType resolvedPosteType = null;

        // ── If caller provided a zoneId, resolve the target poste explicitly ──
        // This is the preferred path when the UI is scoped to a specific poste
        // drawer and the line has multiple postes of the same type.
        if (req.getZoneId() != null) {
            resolvedPoste = posteStatusRepository
                    .findByValidationIdAndZoneId(validationId, req.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Poste status not found for validation=" + validationId
                                    + " zone=" + req.getZoneId()));
            resolvedPosteType = resolvedPoste.getZone() != null
                    ? resolvedPoste.getZone().getPosteType() : null;
        }

        // Mode A — explicit templateId.
        if (req.getTemplateId() != null) {
            template = catalogRepository.findById(req.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("PosteMeasureCatalog", "id", req.getTemplateId()));
            // If zoneId was NOT provided, fall back to first-match by posteType.
            if (resolvedPoste == null) {
                resolvedPosteType = template.getPosteType();
                resolvedPoste = findPosteStatusByType(ticket, resolvedPosteType);
            } else {
                resolvedPosteType = template.getPosteType();
            }
            if (resolvedPoste == null) {
                throw new ValidationException(
                        "Template poste type (" + resolvedPosteType
                                + ") does not match any poste on this ticket's line");
            }
        } else if (measureCode != null && !measureCode.isBlank()) {
            // Mode B — implicit catalog match by (posteType, measureCode), trying every
            // poste of the line in turn.
            for (com.pfe.sageline.entity.ValidationPosteStatus ps : safePosteStatuses(ticket)) {
                if (ps.getZone() == null || ps.getZone().getPosteType() == null) continue;
                var maybeTemplate = catalogRepository
                        .findByPosteTypeAndMeasureCodeAndActiveTrue(ps.getZone().getPosteType(), measureCode);
                if (maybeTemplate.isPresent()) {
                    template = maybeTemplate.get();
                    resolvedPoste = ps;
                    resolvedPosteType = ps.getZone().getPosteType();
                    break;
                }
            }
            // Legacy fallback if the line has no posteStatuses (pre-migration data).
            if (template == null && ticket.getValidationZone() != null) {
                PosteType legacy = ticket.getValidationZone().getPosteType();
                if (legacy != null) {
                    template = catalogRepository
                            .findByPosteTypeAndMeasureCodeAndActiveTrue(legacy, measureCode)
                            .orElse(null);
                    if (template != null) {
                        resolvedPosteType = legacy;
                        // resolvedPoste stays null — there's no posteStatus to attach to.
                    }
                }
            }
        }

        // ticketPosteType is kept only for the error message below.
        PosteType ticketPosteType = resolvedPosteType != null ? resolvedPosteType
                : (ticket.getValidationZone() != null ? ticket.getValidationZone().getPosteType() : null);

        if (template != null) {
            measureCode      = template.getMeasureCode();
            measureLabel     = template.getMeasureLabel();
            category         = template.getCategory();
            unit             = template.getDefaultUnit();
            lowerBound       = template.getDefaultLowerBound();
            upperBound       = template.getDefaultUpperBound();
            // Caller-provided RF context (antenna/freq/modulation) overrides the template,
            // because some catalog templates intentionally leave those null.
            if (antenna == null)          antenna = template.getAntenna();
            if (frequencyMhz == null)     frequencyMhz = template.getFrequencyMhz();
            if (modulationScheme == null) modulationScheme = template.getModulationScheme();
        }

        // Mode C — ad-hoc: nothing was filled from a template.
        // For the verdict to be computable we MUST have measureCode + unit + bounds.
        // measureLabel and category are softer — if missing we default label to the code and
        // category to OTHER. This mirrors how the Sagemcom log importer constructs measures
        // from raw blocks that don't carry a category, and unblocks scenario B-2 when the test
        // payload omits these two fields.
        Map<String, String> missing = new LinkedHashMap<>();
        if (measureCode == null || measureCode.isBlank()) missing.put("measureCode", "ne doit pas être vide");
        if (unit == null || unit.isBlank())               missing.put("unit",        "ne doit pas être vide");
        if (lowerBound == null)                           missing.put("lowerBound",  "ne doit pas être nul");
        if (upperBound == null)                           missing.put("upperBound",  "ne doit pas être nul");
        if (!missing.isEmpty()) {
            throw new ValidationException(
                    "Mesure ad-hoc incomplète : aucun template ne correspond à "
                            + (measureCode == null ? "<code manquant>" : measureCode)
                            + " pour le poste "
                            + (ticketPosteType == null ? "(zone sans poste)" : ticketPosteType.name())
                            + ". Champs manquants : " + missing.keySet());
        }
        if (lowerBound >= upperBound) {
            throw new ValidationException("lowerBound must be strictly less than upperBound");
        }
        if (measureLabel == null || measureLabel.isBlank()) {
            measureLabel = measureCode; // sensible default for ad-hoc / log-import paths
        }
        if (category == null) {
            category = com.pfe.sageline.enums.MeasureCategory.OTHER;
        }

        MeasureStatus status = deviationCalculator.computeStatus(req.getMeasuredValue(), lowerBound, upperBound);
        Double deviationPct = deviationCalculator.computeDeviationPct(req.getMeasuredValue(), lowerBound, upperBound);

        PosteMeasureCatalog finalTemplate = template;
        ValidationMeasure entity = ValidationMeasure.builder()
                .validation(ticket)
                .posteStatus(resolvedPoste)
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
                .mandatoryAtCreation(finalTemplate != null && Boolean.TRUE.equals(finalTemplate.getMandatory()))
                .build();

        ValidationMeasure saved = measureRepository.save(entity);
        publishReadinessAfterCommit(validationId);
        return mapper.toResponse(saved);
    }

    // ── Phase A helpers — line ↔ poste resolution ──────────────────────────

    /** Null-safe accessor for posteStatuses; never returns null. */
    private List<com.pfe.sageline.entity.ValidationPosteStatus> safePosteStatuses(Validation ticket) {
        return ticket.getPosteStatuses() != null ? ticket.getPosteStatuses() : List.of();
    }

    /** Return the first posteStatus on this ticket whose zone has the given posteType, or null. */
    private com.pfe.sageline.entity.ValidationPosteStatus findPosteStatusByType(
            Validation ticket, PosteType posteType) {
        if (posteType == null) return null;
        for (var ps : safePosteStatuses(ticket)) {
            if (ps.getZone() != null && ps.getZone().getPosteType() == posteType) {
                return ps;
            }
        }
        return null;
    }

    @Override
    public List<ValidationMeasureResponse> instantiateFromCatalog(Long validationId) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        // ── Line-wide seeder (post Phase A) ──────────────────────────────────
        // Iterate EVERY poste of the line and seed its catalog onto that poste's
        // ValidationPosteStatus. Previously this method only read the legacy
        // validation_zone_id (the FIRST poste) so 5 of 6 postes ended up empty.
        // ────────────────────────────────────────────────────────────────────
        List<com.pfe.sageline.entity.ValidationPosteStatus> postes = ticket.getPosteStatuses();
        if (postes == null || postes.isEmpty()) {
            // Fallback for pre-line-ticket-migration data: still honour the legacy
            // single-zone path so we don't regress those rows.
            PosteType legacy = ticket.getValidationZone() != null
                    ? ticket.getValidationZone().getPosteType() : null;
            if (legacy == null) {
                return List.of();
            }
            return seedSinglePoste(ticket, null, legacy);
        }

        Long operatorId = securityUtils.getCurrentUserId();
        Instant now = Instant.now();
        List<ValidationMeasure> toCreate = new ArrayList<>();

        for (com.pfe.sageline.entity.ValidationPosteStatus ps : postes) {
            if (ps.getZone() == null || ps.getZone().getPosteType() == null) {
                continue;
            }
            PosteType pt = ps.getZone().getPosteType();
            List<PosteMeasureCatalog> templates =
                    catalogRepository.findByPosteTypeAndActiveOrderByDisplayOrder(pt, true);
            if (templates.isEmpty()) {
                continue;
            }

            // Idempotency is now per-poste: a TEMPS_TEST template may exist on multiple
            // posteTypes; each should only be skipped if THIS poste already has it.
            List<Long> alreadyHere = measureRepository.findCatalogTemplateIdsPresentOnPoste(
                    ps.getId(), templates.stream().map(PosteMeasureCatalog::getId).toList());
            Set<Long> presentSet = new HashSet<>(alreadyHere);

            for (PosteMeasureCatalog t : templates) {
                if (presentSet.contains(t.getId())) continue;
                toCreate.add(ValidationMeasure.builder()
                        .validation(ticket)
                        .posteStatus(ps)
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
                        .mandatoryAtCreation(Boolean.TRUE.equals(t.getMandatory()))
                        .build());
            }
        }

        List<ValidationMeasureResponse> result = mapper.toResponseList(measureRepository.saveAll(toCreate));
        publishReadinessAfterCommit(validationId);
        return result;
    }

    /**
     * Legacy single-poste seeder kept for tickets that have no posteStatuses (e.g.
     * data created before the line-ticket migration). Returns the newly-created rows.
     */
    private List<ValidationMeasureResponse> seedSinglePoste(
            Validation ticket,
            com.pfe.sageline.entity.ValidationPosteStatus posteStatus,
            PosteType posteType) {
        List<PosteMeasureCatalog> templates =
                catalogRepository.findByPosteTypeAndActiveOrderByDisplayOrder(posteType, true);
        if (templates.isEmpty()) return List.of();

        List<Long> alreadyPresent = measureRepository.findCatalogTemplateIdsPresentOnTicket(
                ticket.getId(), templates.stream().map(PosteMeasureCatalog::getId).toList());
        Set<Long> presentSet = new HashSet<>(alreadyPresent);
        Long operatorId = securityUtils.getCurrentUserId();
        Instant now = Instant.now();

        List<ValidationMeasure> toCreate = new ArrayList<>();
        for (PosteMeasureCatalog t : templates) {
            if (presentSet.contains(t.getId())) continue;
            toCreate.add(ValidationMeasure.builder()
                    .validation(ticket)
                    .posteStatus(posteStatus)
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
                    .mandatoryAtCreation(Boolean.TRUE.equals(t.getMandatory()))
                    .build());
        }
        return mapper.toResponseList(measureRepository.saveAll(toCreate));
    }

    /**
     * Per-template instantiation. Idempotent: if the ticket already has a measure for
     * this template, returns the existing row without creating a duplicate. This is
     * what the frontend {@code ValidationMeasureService.fromTemplate(id, templateId)}
     * call relies on for the "instantiate this single template" action.
     */
    @Override
    public ValidationMeasureResponse instantiateOneFromCatalog(Long validationId, Long templateId) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        PosteMeasureCatalog template = catalogRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("PosteMeasureCatalog", "id", templateId));

        // Find the posteStatus on this line whose posteType matches the template.
        // This replaces the legacy "match against validationZone" check, so per-poste
        // attribution works on multi-poste lines.
        com.pfe.sageline.entity.ValidationPosteStatus targetPoste =
                findPosteStatusByType(ticket, template.getPosteType());
        if (targetPoste == null) {
            throw new ValidationException("Template poste type (" + template.getPosteType()
                    + ") does not match any poste of this ticket's line");
        }

        // Idempotency: if this template has already been instantiated on this ticket,
        // surface the existing row instead of erroring or duplicating.
        List<Long> alreadyPresent = measureRepository.findCatalogTemplateIdsPresentOnTicket(
                validationId, List.of(templateId));
        if (!alreadyPresent.isEmpty()) {
            return mapper.toResponseList(
                    measureRepository.findAllByValidationIdFetchTemplate(validationId)).stream()
                    .filter(m -> templateId.equals(m.catalogTemplateId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Existing measure for template " + templateId + " disappeared between checks"));
        }

        ValidationMeasure entity = ValidationMeasure.builder()
                .validation(ticket)
                .posteStatus(targetPoste)
                .catalogTemplate(template)
                .measureCode(template.getMeasureCode())
                .measureLabel(template.getMeasureLabel())
                .category(template.getCategory())
                .unit(template.getDefaultUnit())
                .lowerBound(template.getDefaultLowerBound())
                .upperBound(template.getDefaultUpperBound())
                .measuredValue(null)
                .status(MeasureStatus.NOT_EXECUTED)
                .deviationPct(null)
                .antenna(template.getAntenna())
                .frequencyMhz(template.getFrequencyMhz())
                .modulationScheme(template.getModulationScheme())
                .enteredBy(securityUtils.getCurrentUserId())
                .measuredAt(Instant.now())
                .mandatoryAtCreation(Boolean.TRUE.equals(template.getMandatory()))
                .build();

        ValidationMeasure saved = measureRepository.save(entity);
        publishReadinessAfterCommit(validationId);
        return mapper.toResponse(saved);
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

        ValidationMeasureResponse updated = mapper.toResponse(measureRepository.save(m));
        publishReadinessAfterCommit(validationId);
        return updated;
    }

    /**
     * Bulk-update measuredValue across N rows. Each row is processed independently:
     * failures are recorded in the result, successes are persisted. We publish ONE
     * coalesced readiness snapshot at the end rather than N (one per save).
     */
    @Override
    public BatchValidationMeasureResponse batchUpdate(Long validationId, BatchUpdateMeasureRequest req) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        List<BatchValidationMeasureResponse.Item> rows = new ArrayList<>();
        int ok = 0;
        int failed = 0;
        Long operator = securityUtils.getCurrentUserId();
        Instant now = Instant.now();

        List<BatchUpdateMeasureRequest.Item> items = req.getItems();
        for (int i = 0; i < items.size(); i++) {
            BatchUpdateMeasureRequest.Item it = items.get(i);
            try {
                ValidationMeasure m = measureRepository.findByIdAndValidationId(it.getId(), validationId)
                        .orElse(null);
                if (m == null) {
                    failed++;
                    rows.add(BatchValidationMeasureResponse.Item.builder()
                            .index(i).status("error")
                            .error(BatchValidationMeasureResponse.ErrorInfo.builder()
                                    .code("NOT_FOUND")
                                    .message("Measure " + it.getId() + " not on ticket " + validationId)
                                    .build())
                            .build());
                    continue;
                }

                m.setMeasuredValue(it.getMeasuredValue());
                m.setStatus(deviationCalculator.computeStatus(m.getMeasuredValue(), m.getLowerBound(), m.getUpperBound()));
                m.setDeviationPct(deviationCalculator.computeDeviationPct(m.getMeasuredValue(), m.getLowerBound(), m.getUpperBound()));
                m.setEnteredBy(operator);
                m.setMeasuredAt(now);

                ValidationMeasureResponse resp = mapper.toResponse(measureRepository.save(m));
                ok++;
                rows.add(BatchValidationMeasureResponse.Item.builder()
                        .index(i).status("ok").measure(resp).build());
            } catch (Exception ex) {
                failed++;
                rows.add(BatchValidationMeasureResponse.Item.builder()
                        .index(i).status("error")
                        .error(BatchValidationMeasureResponse.ErrorInfo.builder()
                                .code("UPDATE_FAILED")
                                .message(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                                .build())
                        .build());
            }
        }

        if (ok > 0) {
            publishReadinessAfterCommit(validationId);
        }

        return BatchValidationMeasureResponse.builder()
                .results(rows)
                .summary(BatchValidationMeasureResponse.Summary.builder()
                        .succeeded(ok).failed(failed).build())
                .build();
    }

    @Override
    public void delete(Long validationId, Long measureId) {
        Validation ticket = loadTicket(validationId);
        editabilityGuard.requireEditable(ticket);

        ValidationMeasure m = measureRepository.findByIdAndValidationId(measureId, validationId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationMeasure", "id", measureId));
        measureRepository.delete(m);
        publishReadinessAfterCommit(validationId);
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

        // Legacy posteType only used as the LAST-resort fallback (pre-line-ticket data).
        PosteType legacyPosteType = ticket.getValidationZone() != null
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

            com.pfe.sageline.entity.ValidationPosteStatus rowPoste = null;

            // Mode A — explicit templateId.
            if (entry.getTemplateId() != null) {
                var optTemplate = catalogRepository.findById(entry.getTemplateId());
                if (optTemplate.isEmpty()) {
                    failures.add(new BatchMeasureValidationException.FailedEntry(
                            i, "UNKNOWN_TEMPLATE", "templateId " + entry.getTemplateId() + " not found"));
                    continue;
                }
                template = optTemplate.get();
                rowPoste = findPosteStatusByType(ticket, template.getPosteType());
                if (rowPoste == null) {
                    failures.add(new BatchMeasureValidationException.FailedEntry(
                            i, "OWNER_MISMATCH",
                            "Template poste type " + template.getPosteType()
                                    + " has no matching poste on this line"));
                    continue;
                }
            } else if (measureCode != null && !measureCode.isBlank()) {
                // Mode B — walk every poste of the line; first one whose catalog
                // contains this measure code wins.
                for (var ps : safePosteStatuses(ticket)) {
                    if (ps.getZone() == null || ps.getZone().getPosteType() == null) continue;
                    var maybe = catalogRepository
                            .findByPosteTypeAndMeasureCodeAndActiveTrue(ps.getZone().getPosteType(), measureCode);
                    if (maybe.isPresent()) {
                        template = maybe.get();
                        rowPoste = ps;
                        break;
                    }
                }
                // Legacy fallback for pre-migration tickets.
                if (template == null && legacyPosteType != null) {
                    template = catalogRepository
                            .findByPosteTypeAndMeasureCodeAndActiveTrue(legacyPosteType, measureCode)
                            .orElse(null);
                }
            }

            if (template != null) {
                measureCode      = template.getMeasureCode();
                measureLabel     = template.getMeasureLabel();
                category         = template.getCategory();
                unit             = template.getDefaultUnit();
                lowerBound       = template.getDefaultLowerBound();
                upperBound       = template.getDefaultUpperBound();
                if (antenna == null)          antenna = template.getAntenna();
                if (frequencyMhz == null)     frequencyMhz = template.getFrequencyMhz();
                if (modulationScheme == null) modulationScheme = template.getModulationScheme();
            }

            // Mode C — ad-hoc: post-resolution sanity check.
            // Same softening as in create(): verdict-critical fields (code, unit, bounds) are
            // hard requirements; label defaults to code and category defaults to OTHER.
            if (measureCode == null || measureCode.isBlank()
                    || unit == null || unit.isBlank()
                    || lowerBound == null || upperBound == null) {
                failures.add(new BatchMeasureValidationException.FailedEntry(
                        i, "MISSING_FIELDS",
                        "Ad-hoc measure missing required fields (measureCode, unit, lowerBound, upperBound) "
                                + "and no matching catalog template found"));
                continue;
            }
            if (lowerBound >= upperBound) {
                failures.add(new BatchMeasureValidationException.FailedEntry(
                        i, "INVALID_BOUNDS", "lowerBound must be strictly less than upperBound"));
                continue;
            }
            if (measureLabel == null || measureLabel.isBlank()) {
                measureLabel = measureCode;
            }
            if (category == null) {
                category = com.pfe.sageline.enums.MeasureCategory.OTHER;
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

            resolved.add(new ResolvedEntry(template, rowPoste, measureCode, measureLabel, category,
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
                    .posteStatus(r.posteStatus)
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
                    .mandatoryAtCreation(r.template != null && Boolean.TRUE.equals(r.template.getMandatory()))
                    .build();
        }).toList();

        List<ValidationMeasureResponse> results = mapper.toResponseList(measureRepository.saveAll(entities));
        publishReadinessAfterCommit(validationId);
        return results;
    }

    private void publishReadinessAfterCommit(Long validationId) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        WorkflowReadinessDTO snapshot = readinessService.computeReadiness(validationId, null);
                        messagingTemplate.convertAndSend(
                            "/topic/validation." + validationId + ".readiness", snapshot);
                    } catch (Exception e) {
                        log.warn("Readiness STOMP push failed for ticket {}: {}", validationId, e.getMessage());
                    }
                }
            });
    }

    private Validation loadTicket(Long validationId) {
        return validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation", "id", validationId));
    }

    private record ResolvedEntry(
            PosteMeasureCatalog template,
            com.pfe.sageline.entity.ValidationPosteStatus posteStatus,
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
