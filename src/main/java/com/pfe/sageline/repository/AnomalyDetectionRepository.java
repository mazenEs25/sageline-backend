package com.pfe.sageline.repository;

import com.pfe.sageline.entity.AnomalyDetection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalyDetectionRepository extends JpaRepository<AnomalyDetection,Long> {
    List<AnomalyDetection> findBySeverity(String severity);
}
