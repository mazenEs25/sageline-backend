package com.pfe.sageline.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "non_conformity_predictions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class    NonConformityPrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel; // "OK", "RISQUE", "NC"

    @Column(name = "confidence", nullable = false)
    private Double confidence;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id")
    private Validation validation;

    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    @PrePersist
    protected void onCreate() {
        if (predictedAt == null) {
            predictedAt = LocalDateTime.now();
        }
    }
}
