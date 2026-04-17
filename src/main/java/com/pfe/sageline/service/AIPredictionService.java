package com.pfe.sageline.service;

import com.pfe.sageline.dtos.response.NonConformityPredictionResponseDTO;
import com.pfe.sageline.entity.NonConformityPrediction;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.repository.NonConformityPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIPredictionService {

    private final NonConformityPredictionRepository predictionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.service.url:http://localhost:5000}")
    private String aiServiceUrl;

    /**
     * Prédire le risque de non-conformité pour une validation
     */
    public NonConformityPrediction predictNonConformity(Validation validation) {
        log.info("Requesting AI prediction for validation {}", validation.getId());

        try {
            // Calculer les statistiques des résultats
            int resultsCount = validation.getValidationResults() != null ?
                    validation.getValidationResults().size() : 0;

            double avgDeviation = calculateAvgDeviation(validation);
            double maxDeviation = calculateMaxDeviation(validation);

            Long zoneId = validation.getValidationZone().getId();
            Long lineId = validation.getValidationZone().getProductionLine().getId();

            // Appeler le service Python
            NonConformityPredictionResponseDTO predictionDTO = callPythonService(
                    zoneId, lineId, resultsCount, avgDeviation, maxDeviation
            );

            // ✅ VÉRIFIER SI UNE PRÉDICTION EXISTE DÉJÀ
            Optional<NonConformityPrediction> existingPrediction =
                    predictionRepository.findByValidationId(validation.getId());

            NonConformityPrediction prediction;

            if (existingPrediction.isPresent()) {
                // ✅ METTRE À JOUR la prédiction existante
                prediction = existingPrediction.get();
                prediction.setRiskScore(predictionDTO.getRiskScore());
                prediction.setRiskLevel(predictionDTO.getRiskLevel());
                prediction.setConfidence(predictionDTO.getConfidence());
                prediction.setPredictedAt(LocalDateTime.now());
                log.info("Updated existing prediction for validation {}", validation.getId());
            } else {
                // ✅ CRÉER une nouvelle prédiction
                prediction = new NonConformityPrediction();
                prediction.setValidation(validation);
                prediction.setRiskScore(predictionDTO.getRiskScore());
                prediction.setRiskLevel(predictionDTO.getRiskLevel());
                prediction.setConfidence(predictionDTO.getConfidence());
                prediction.setPredictedAt(LocalDateTime.now());
                log.info("Created new prediction for validation {}", validation.getId());
            }

            // Sauvegarder (save ou update selon le cas)
            NonConformityPrediction savedPrediction = predictionRepository.save(prediction);

            log.info("AI Prediction saved - Risk: {}, Level: {}",
                    savedPrediction.getRiskScore(), savedPrediction.getRiskLevel());

            return savedPrediction;

        } catch (Exception e) {
            log.error("Error calling AI service: {}", e.getMessage(), e);
            // Retourner une prédiction par défaut
            return createDefaultPrediction(validation);
        }
    }

    /**
     * Appeler le service Python Flask
     */
    private NonConformityPredictionResponseDTO callPythonService(
            Long zoneId, Long lineId, int resultsCount,
            double avgDeviation, double maxDeviation) {

        // Préparer la requête
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("zoneId", zoneId);
        requestBody.put("lineId", lineId);
        requestBody.put("resultsCount", resultsCount);
        requestBody.put("avgDeviation", avgDeviation);
        requestBody.put("maxDeviation", maxDeviation);
        requestBody.put("hourOfDay", LocalDateTime.now().getHour());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Appel HTTP au service Python
        String url = aiServiceUrl + "/predict";
        log.debug("Calling AI service at: {}", url);
        log.debug("Request body: {}", requestBody);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();

            NonConformityPredictionResponseDTO prediction = new NonConformityPredictionResponseDTO();
            prediction.setRiskScore(((Number) body.get("riskScore")).doubleValue());
            prediction.setRiskLevel((String) body.get("riskLevel"));
            prediction.setConfidence(((Number) body.get("confidence")).doubleValue());
            prediction.setPredictedAt(LocalDateTime.now());

            log.debug("Prediction received: {}", prediction);

            return prediction;
        }

        throw new RuntimeException("Failed to get prediction from AI service");
    }

    /**
     * Calculer l'écart moyen des résultats
     */
    private double calculateAvgDeviation(Validation validation) {
        if (validation.getValidationResults() == null || validation.getValidationResults().isEmpty()) {
            return 0.0;
        }

        double totalDeviation = validation.getValidationResults().stream()
                .mapToDouble(result -> {
                    double measured = result.getMeasuredValue();
                    double expected = result.getExpectedValue();
                    if (expected == 0) return 0;
                    return Math.abs((measured - expected) / expected) * 100;
                })
                .sum();

        return totalDeviation / validation.getValidationResults().size();
    }

    /**
     * Calculer l'écart maximum des résultats
     */
    private double calculateMaxDeviation(Validation validation) {
        if (validation.getValidationResults() == null || validation.getValidationResults().isEmpty()) {
            return 0.0;
        }

        return validation.getValidationResults().stream()
                .mapToDouble(result -> {
                    double measured = result.getMeasuredValue();
                    double expected = result.getExpectedValue();
                    if (expected == 0) return 0;
                    return Math.abs((measured - expected) / expected) * 100;
                })
                .max()
                .orElse(0.0);
    }

    /**
     * Créer une prédiction par défaut si le service IA est indisponible
     */
    private NonConformityPrediction createDefaultPrediction(Validation validation) {
        log.warn("Using default prediction for validation {}", validation.getId());

        NonConformityPrediction prediction = new NonConformityPrediction();
        prediction.setValidation(validation);
        prediction.setRiskScore(0.5);
        prediction.setRiskLevel("MOYEN");
        prediction.setConfidence(0.5);
        prediction.setPredictedAt(LocalDateTime.now());

        return predictionRepository.save(prediction);
    }

    /**
     * Vérifier si le service IA est disponible
     */
    public boolean isServiceAvailable() {
        try {
            String url = aiServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            boolean available = response.getStatusCode().is2xxSuccessful();
            log.info("AI service availability check: {}", available);
            return available;
        } catch (Exception e) {
            log.warn("AI service is not available: {}", e.getMessage());
            return false;
        }
    }
}