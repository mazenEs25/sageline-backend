package com.pfe.sageline.repository;

import com.pfe.sageline.entity.AnomalyDetection;
import com.pfe.sageline.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AnomalyDetectionRepository extends JpaRepository<AnomalyDetection, Long> {

    List<AnomalyDetection> findByValidationId(Long validationId);

    List<AnomalyDetection> findByDetectedAtAfter(LocalDateTime since);

    List<AnomalyDetection> findBySeverity(Severity severity);
}