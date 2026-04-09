package com.pfe.sageline.service;

import com.pfe.sageline.entity.*;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.repository.ValidationResultRepository;
import com.pfe.sageline.repository.AnomalyDetectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnomalyDetectionService {

    private final ValidationRepository validationRepository;
    private final ValidationResultRepository resultRepository;
    private final AnomalyDetectionRepository anomalyRepository;

    // Thresholds
    private static final double Z_SCORE_THRESHOLD = 2.5;  // Standard deviations
    private static final double ANOMALY_SCORE_CRITICAL = 0.8;
    private static final double ANOMALY_SCORE_WARNING = 0.5;

    public AnomalyDetectionService(ValidationRepository validationRepository,
                                   ValidationResultRepository resultRepository,
                                   AnomalyDetectionRepository anomalyRepository) {
        this.validationRepository = validationRepository;
        this.resultRepository = resultRepository;
        this.anomalyRepository = anomalyRepository;
    }

    /**
     * MODEL 3: Detect anomalies for a specific validation.
     * Analyzes deviation patterns, timing, and failure frequency.
     */
    public List<AnomalyResult> detectAnomalies(Long validationId) {
        List<ValidationResult> results = resultRepository.findByValidationId(validationId);
        Validation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new RuntimeException("Validation non trouvée"));

        List<AnomalyResult> anomalies = new ArrayList<>();

        // Detection 1: Deviation anomaly (per measurement)
        anomalies.addAll(detectDeviationAnomalies(results, validation));

        // Detection 2: Pattern anomaly (time-based)
        anomalies.addAll(detectTimePatternAnomalies(validation));

        // Detection 3: Failure frequency anomaly
        anomalies.addAll(detectFailureFrequencyAnomalies(validation));

        // Save detected anomalies to database
        for (AnomalyResult anomaly : anomalies) {
            saveAnomaly(validationId, anomaly);
        }

        return anomalies;
    }

    /**
     * Detect anomalies across all active validations.
     */
    public List<AnomalyResult> scanAllActive() {
        List<Validation> activeValidations = validationRepository.findByStatus(ValidationStatus.EN_COURS);
        List<AnomalyResult> allAnomalies = new ArrayList<>();

        for (Validation v : activeValidations) {
            List<ValidationResult> results = resultRepository.findByValidationId(v.getId());
            allAnomalies.addAll(detectDeviationAnomalies(results, v));
        }

        return allAnomalies;
    }

    /**
     * Get anomaly history for a zone.
     */
    public Map<String, Object> getZoneAnomalyReport(Long zoneId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AnomalyDetection> anomalies = anomalyRepository.findByDetectedAtAfter(since);

        // Filter by zone (through validation)
        List<AnomalyDetection> zoneAnomalies = anomalies.stream()
                .filter(a -> {
                    Validation v = validationRepository.findById(a.getValidationId()).orElse(null);
                    return v != null && v.getValidationZone() != null
                            && v.getValidationZone().getId().equals(zoneId);
                })
                .collect(Collectors.toList());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("zoneId", zoneId);
        report.put("period", days + " days");
        report.put("totalAnomalies", zoneAnomalies.size());
        report.put("critical", zoneAnomalies.stream()
                .filter(a -> "CRITIQUE".equals(a.getSeverity())).count());
        report.put("warning", zoneAnomalies.stream()
                .filter(a -> "ALERTE".equals(a.getSeverity())).count());
        report.put("anomalies", zoneAnomalies);

        return report;
    }

    // ══════════════════════════════════════════════
    // DETECTION ALGORITHMS
    // ══════════════════════════════════════════════

    /**
     * Detection 1: Z-Score based deviation anomaly.
     * Compares each measurement against historical data for the same parameter.
     */
    private List<AnomalyResult> detectDeviationAnomalies(List<ValidationResult> results,
                                                         Validation validation) {
        List<AnomalyResult> anomalies = new ArrayList<>();

        for (ValidationResult result : results) {
            // Get historical values for this parameter
            List<Double> historicalDeviations = getHistoricalDeviations(
                    result.getParameter(),
                    validation.getValidationZone().getId()
            );

            if (historicalDeviations.size() < 5) {
                continue; // Not enough data for statistical analysis
            }

            // Calculate current deviation
            double currentDeviation = Math.abs(result.getMeasuredValue() - result.getExpectedValue())
                    / result.getExpectedValue() * 100.0;

            // Calculate z-score
            double mean = historicalDeviations.stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            double stdDev = calculateStdDev(historicalDeviations, mean);

            if (stdDev == 0) continue;

            double zScore = Math.abs((currentDeviation - mean) / stdDev);

            if (zScore > Z_SCORE_THRESHOLD) {
                double anomalyScore = Math.min(1.0, zScore / 5.0); // Normalize to 0-1
                String severity = anomalyScore >= ANOMALY_SCORE_CRITICAL ? "CRITIQUE" : "ALERTE";

                anomalies.add(new AnomalyResult(
                        "DEVIATION",
                        severity,
                        String.format("Paramètre '%s': écart de %.1f%% (z-score: %.2f). " +
                                        "Moyenne historique: %.1f%%, écart-type: %.1f%%",
                                result.getParameter(), currentDeviation, zScore, mean, stdDev),
                        anomalyScore,
                        result.getParameter()
                ));
            }
        }

        return anomalies;
    }

    /**
     * Detection 2: Time pattern anomaly.
     * Detects validations at unusual hours or with unusual duration.
     */
    private List<AnomalyResult> detectTimePatternAnomalies(Validation validation) {
        List<AnomalyResult> anomalies = new ArrayList<>();

        // Check for unusual hour (night shifts: 22h-6h)
        int hour = validation.getStartDate().getHour();
        if (hour >= 22 || hour < 6) {
            anomalies.add(new AnomalyResult(
                    "HORAIRE",
                    "ALERTE",
                    String.format("Validation lancée à %02dh — heure inhabituelle (période nocturne). " +
                            "Risque de fatigue accru.", hour),
                    0.6,
                    "timing"
            ));
        }

        // Check for unusually long validation (if still active)
        if (validation.getEndDate() == null && validation.getStartDate() != null) {
            long hoursActive = ChronoUnit.HOURS.between(validation.getStartDate(), LocalDateTime.now());
            if (hoursActive > 8) {
                double score = Math.min(1.0, hoursActive / 24.0);
                anomalies.add(new AnomalyResult(
                        "DUREE",
                        score > 0.7 ? "CRITIQUE" : "ALERTE",
                        String.format("Validation active depuis %d heures — durée anormalement longue. " +
                                "Vérifiez si elle doit être clôturée.", hoursActive),
                        score,
                        "duration"
                ));
            }
        }

        return anomalies;
    }

    /**
     * Detection 3: Failure frequency anomaly.
     * Detects if a zone is producing more failures than usual.
     */
    private List<AnomalyResult> detectFailureFrequencyAnomalies(Validation validation) {
        List<AnomalyResult> anomalies = new ArrayList<>();

        if (validation.getValidationZone() == null) return anomalies;

        Long zoneId = validation.getValidationZone().getId();

        // Count recent failures in this zone (last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Validation> recentValidations = validationRepository
                .findByValidationZoneIdAndStartDateAfter(zoneId, weekAgo);

        long totalRecent = recentValidations.size();
        long failuresRecent = recentValidations.stream()
                .filter(v -> "NON_CONFORME".equals(v.getStatus().name()))
                .count();

        if (totalRecent >= 3) {
            double failureRate = (double) failuresRecent / totalRecent;

            // Get historical failure rate (last 30 days)
            LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
            List<Validation> monthValidations = validationRepository
                    .findByValidationZoneIdAndStartDateAfter(zoneId, monthAgo);

            long totalMonth = monthValidations.size();
            long failuresMonth = monthValidations.stream()
                    .filter(v -> "NON_CONFORME".equals(v.getStatus().name()))
                    .count();

            double historicalRate = totalMonth > 0 ? (double) failuresMonth / totalMonth : 0.2;

            // If recent failure rate is significantly higher than historical
            if (failureRate > historicalRate * 2 && failureRate > 0.3) {
                anomalies.add(new AnomalyResult(
                        "FREQUENCE",
                        "CRITIQUE",
                        String.format("Taux d'échec récent: %.0f%% (vs %.0f%% historique). " +
                                        "%d échecs sur %d validations cette semaine.",
                                failureRate * 100, historicalRate * 100, failuresRecent, totalRecent),
                        Math.min(1.0, failureRate * 1.5),
                        "failure_rate"
                ));
            }
        }

        return anomalies;
    }

    // ══════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════

    private List<Double> getHistoricalDeviations(String parameter, Long zoneId) {
        // Get all results for this parameter in this zone
        List<ValidationResult> historical = resultRepository.findAll().stream()
                .filter(r -> parameter.equals(r.getParameter()))
                .collect(Collectors.toList());

        return historical.stream()
                .map(r -> Math.abs(r.getMeasuredValue() - r.getExpectedValue())
                        / r.getExpectedValue() * 100.0)
                .collect(Collectors.toList());
    }

    private double calculateStdDev(List<Double> values, double mean) {
        double sumSquares = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquares / values.size());
    }

    private void saveAnomaly(Long validationId, AnomalyResult result) {
        AnomalyDetection anomaly = new AnomalyDetection();
        anomaly.setValidationId(validationId);
        anomaly.setAnomalyType(AnomalyType.valueOf(result.type));
        anomaly.setSeverity(Severity.valueOf(result.severity));
        anomaly.setDescription(result.description);
        anomaly.setDetectedAt(LocalDateTime.now());
        anomalyRepository.save(anomaly);
    }

    // ─── Result DTO ───
    public static class AnomalyResult {
        public String type;        // DEVIATION, HORAIRE, DUREE, FREQUENCE
        public String severity;    // ALERTE, CRITIQUE
        public String description;
        public double score;       // 0.0 to 1.0
        public String parameter;   // Which parameter triggered it

        public AnomalyResult(String type, String severity, String description,
                             double score, String parameter) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.score = score;
            this.parameter = parameter;
        }
    }
}