package com.pfe.sageline.entity;

import jakarta.persistence.*;
import lombok.*;
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

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ToolStatus status; // DISPONIBLE, EN_UTILISATION, EN_MAINTENANCE

    private LocalDateTime lastMaintenanceDate;


    private LocalDateTime nextMaintenanceDate;

    private LocalDateTime createdAt;

    // ML features
    @Column(name = "success_rate")
    private Double successRate;       // Historical success rate (0-100)

    @Column(name = "usage_count")
    private Integer usageCount;       // How many times used

    @Column(name = "avg_validation_time")
    private Double avgValidationTime; // Average time in minutes

    @Column(name = "compatible_zones")
    private String compatibleZones;   // Comma-separated zone IDs

    @Column(name = "compatible_lines")
    private String compatibleLines;   // Comma-separated line IDs

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}