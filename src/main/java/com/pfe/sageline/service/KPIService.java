package com.pfe.sageline.service;

import com.pfe.sageline.dtos.KPIResponse;
import com.pfe.sageline.entity.KPI;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationStatus;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.KPIMapper;
import com.pfe.sageline.repository.KPIRepository;
import com.pfe.sageline.repository.ProductionLineRepository;
import com.pfe.sageline.repository.ValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KPIService {

    private final KPIRepository kpiRepository;
    private final ValidationRepository validationRepository;
    private final ProductionLineRepository productionLineRepository;
    private final KPIMapper kpiMapper;  // ← AJOUTER


    /**
     * Récupérer tous les KPIs d'une ligne de production
     */
    @Transactional(readOnly = true)
    public List<KPIResponse> getKPIsByLine(Long lineId) {
        log.info("Récupération des KPIs pour la ligne {}", lineId);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        List<KPI> kpis = kpiRepository.findByProductionLineId(lineId);
        return kpis.stream()
                .map(kpiMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupérer les derniers KPIs d'une ligne
     */
    @Transactional(readOnly = true)
    public List<KPIResponse> getLatestKPIsByLine(Long lineId) {
        log.info("Récupération des derniers KPIs pour la ligne {}", lineId);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        List<KPI> kpis = kpiRepository.findLatestByLine(lineId);
        return kpis.stream()
                .map(kpiMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupérer les KPIs d'une ligne sur une période
     */
    @Transactional(readOnly = true)
    public List<KPIResponse> getKPIsByLineAndDateRange(Long lineId, LocalDate start, LocalDate end) {
        log.info("Récupération des KPIs pour la ligne {} entre {} et {}", lineId, start, end);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        List<KPI> kpis = kpiRepository.findByLineAndDateRange(lineId, start, end);
        return kpis.stream()
                .map(kpiMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calculer le taux de conformité d'une ligne (toutes périodes)
     */
    @Transactional(readOnly = true)
    public Double calculateConformityRate(Long lineId) {
        log.info("Calcul du taux de conformité pour la ligne {}", lineId);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        List<Validation> allValidations = validationRepository.findByProductionLineId(lineId);

        long conformeCount = allValidations.stream()
                .filter(v -> v.getStatus() == ValidationStatus.CONFORME)
                .count();

        long nonConformeCount = allValidations.stream()
                .filter(v -> v.getStatus() == ValidationStatus.NON_CONFORME)
                .count();

        long totalCount = conformeCount + nonConformeCount;

        if (totalCount == 0) {
            return 0.0;
        }

        double rate = (conformeCount * 100.0) / totalCount;
        return Math.round(rate * 100.0) / 100.0; // Arrondir à 2 décimales
    }

    /**
     * Calculer le taux de conformité d'une ligne sur une période
     */
    @Transactional(readOnly = true)
    public Double calculateConformityRateForPeriod(Long lineId, LocalDate start, LocalDate end) {
        log.info("Calcul du taux de conformité pour la ligne {} entre {} et {}", lineId, start, end);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        Long conformeCount = validationRepository.countConformeByLineAndDateRange(lineId, startDateTime);
        Long nonConformeCount = validationRepository.countNonConformeByLineAndDateRange(lineId, startDateTime);

        Long totalCount = conformeCount + nonConformeCount;

        if (totalCount == 0) {
            return 0.0;
        }

        double rate = (conformeCount * 100.0) / totalCount;
        return Math.round(rate * 100.0) / 100.0;
    }

    /**
     * Compter les validations par statut pour une ligne
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countValidationsByLine(Long lineId) {
        log.info("Comptage des validations pour la ligne {}", lineId);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        Map<String, Long> counts = new HashMap<>();

        long conformeCount = validationRepository.findByProductionLineIdAndStatus(
                lineId, ValidationStatus.CONFORME).size();
        long nonConformeCount = validationRepository.findByProductionLineIdAndStatus(
                lineId, ValidationStatus.NON_CONFORME).size();
        long enCoursCount = validationRepository.findByProductionLineIdAndStatus(
                lineId, ValidationStatus.EN_COURS).size();

        counts.put("total", conformeCount + nonConformeCount + enCoursCount);
        counts.put("conforme", conformeCount);
        counts.put("non_conforme", nonConformeCount);
        counts.put("en_cours", enCoursCount);

        return counts;
    }

    /**
     * Compter les validations par statut pour une ligne sur une période
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countValidationsByLineForPeriod(Long lineId, LocalDate start, LocalDate end) {
        log.info("Comptage des validations pour la ligne {} entre {} et {}", lineId, start, end);

        if (!productionLineRepository.existsById(lineId)) {
            throw new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId);
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        Map<String, Long> counts = new HashMap<>();

        Long conformeCount = validationRepository.countConformeByLineAndDateRange(lineId, startDateTime);
        Long nonConformeCount = validationRepository.countNonConformeByLineAndDateRange(lineId, startDateTime);

        long enCoursCount = validationRepository.findByDateRange(startDateTime, endDateTime).stream()
                .filter(v -> v.getStatus() == ValidationStatus.EN_COURS)
                .filter(v -> v.getValidationZone().getProductionLine().getId().equals(lineId))
                .count();

        counts.put("total", conformeCount + nonConformeCount + enCoursCount);
        counts.put("conforme", conformeCount);
        counts.put("non_conforme", nonConformeCount);
        counts.put("en_cours", enCoursCount);

        return counts;
    }

    /**
     * Dashboard global avec tous les KPIs principaux
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalDashboard() {
        log.info("Génération du dashboard global");

        Map<String, Object> dashboard = new HashMap<>();

        // Total des validations
        Long totalValidations = validationRepository.count();
        dashboard.put("totalValidations", totalValidations);

        // Validations actives (EN_COURS)
        Long activeValidations = Long.valueOf(validationRepository.findActiveValidations().size());
        dashboard.put("activeValidations", activeValidations);

        // Taux de conformité global
        long conformeCount = validationRepository.findByStatus(ValidationStatus.CONFORME).size();
        long nonConformeCount = validationRepository.findByStatus(ValidationStatus.NON_CONFORME).size();
        double conformityRate = 0.0;
        if ((conformeCount + nonConformeCount) > 0) {
            conformityRate = (conformeCount * 100.0) / (conformeCount + nonConformeCount);
            conformityRate = Math.round(conformityRate * 100.0) / 100.0;
        }
        dashboard.put("conformityRate", conformityRate);

        // Validations aujourd'hui
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        Long validationsToday = Long.valueOf(validationRepository.findByDateRange(today, endOfDay).size());
        dashboard.put("validationsToday", validationsToday);

        // Non-conformités cette semaine
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();
        Long nonConformitiesThisWeek = validationRepository.countByStatusSinceDate(
                ValidationStatus.NON_CONFORME, weekStart);
        dashboard.put("nonConformitiesThisWeek", nonConformitiesThisWeek);

        // Nombre total de lignes
        Long totalLines = productionLineRepository.count();
        dashboard.put("totalLines", totalLines);

        // Lignes actives
        Long activeLines = Long.valueOf(productionLineRepository.findByActive(true).size());
        dashboard.put("activeLines", activeLines);

        return dashboard;
    }

    /**
     * Dashboard pour une ligne spécifique
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLineDashboard(Long lineId) {
        log.info("Génération du dashboard pour la ligne {}", lineId);

        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId));

        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("lineId", line.getId());
        dashboard.put("lineName", line.getName());
        dashboard.put("lineCode", line.getCode());
        dashboard.put("lineActive", line.getActive());

        // Statistiques de validation
        Map<String, Long> counts = countValidationsByLine(lineId);
        dashboard.put("totalValidations", counts.get("total"));
        dashboard.put("activeValidations", counts.get("en_cours"));

        // Taux de conformité
        Double conformityRate = calculateConformityRate(lineId);
        dashboard.put("conformityRate", conformityRate);

        // Dernière validation
        List<KPI> latestKPIs = kpiRepository.findLatestByLine(lineId);
        if (!latestKPIs.isEmpty()) {
            dashboard.put("lastKPICalculation", latestKPIs.get(0).getCalculationDate());
        }

        // Nombre de zones
        Long zonesCount = productionLineRepository.countZonesByLineId(lineId);
        dashboard.put("zonesCount", zonesCount);

        return dashboard;
    }

    /**
     * Recalculer tous les KPIs pour une ligne
     */
    public List<KPIResponse> recalculateKPIsForLine(Long lineId) {
        log.info("Recalcul des KPIs pour la ligne {}", lineId);

        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne de production non trouvée avec l'ID: " + lineId));

        LocalDate today = LocalDate.now();

        // KPI 1: Taux de conformité
        Double conformityRate = calculateConformityRate(lineId);
        KPI kpiConformity = new KPI();
        kpiConformity.setName("Taux de conformité");
        kpiConformity.setValue(conformityRate);
        kpiConformity.setCalculationDate(today);
        kpiConformity.setProductionLine(line);
        kpiRepository.save(kpiConformity);

        // KPI 2: Nombre total de validations
        Map<String, Long> counts = countValidationsByLine(lineId);
        KPI kpiTotal = new KPI();
        kpiTotal.setName("Total validations");
        kpiTotal.setValue(counts.get("total").doubleValue());
        kpiTotal.setCalculationDate(today);
        kpiTotal.setProductionLine(line);
        kpiRepository.save(kpiTotal);

        // KPI 3: Nombre de non-conformités
        KPI kpiNonConforme = new KPI();
        kpiNonConforme.setName("Non-conformités");
        kpiNonConforme.setValue(counts.get("non_conforme").doubleValue());
        kpiNonConforme.setCalculationDate(today);
        kpiNonConforme.setProductionLine(line);
        kpiRepository.save(kpiNonConforme);

        log.info("KPIs recalculés avec succès pour la ligne {}", lineId);

        return getLatestKPIsByLine(lineId);
    }
}