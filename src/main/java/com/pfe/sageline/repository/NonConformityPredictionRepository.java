package com.pfe.sageline.repository;

import com.pfe.sageline.entity.NonConformityPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NonConformityPredictionRepository extends JpaRepository<NonConformityPrediction,Long> {
    Optional<NonConformityPrediction> findByValidationId(Long validationId);

    List<NonConformityPrediction> findByRiskLevel(String riskLevel);

}
