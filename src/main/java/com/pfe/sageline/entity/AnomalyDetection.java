package com.pfe.sageline.entity;

import com.pfe.sageline.enums.AnomalyType;
import com.pfe.sageline.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_detections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long validationId;

    @Enumerated(EnumType.STRING)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private Severity severity;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        this.detectedAt = LocalDateTime.now();
    }
}