package com.pfe.sageline.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "tool_recommendations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "recommended_at", nullable = false)
    private LocalDateTime recommendedAt;

    @PrePersist
    protected void onCreate() {
        if (recommendedAt == null) {
            recommendedAt = LocalDateTime.now();
        }
    }
}
