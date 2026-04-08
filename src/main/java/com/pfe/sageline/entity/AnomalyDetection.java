package com.pfe.sageline.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @Column(name = "anomaly_score", nullable = false)
    private Double anomalyScore;

    @Column(nullable = false)
    private String severity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }
}
