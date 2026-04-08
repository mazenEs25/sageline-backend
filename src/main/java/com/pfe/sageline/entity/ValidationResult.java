package com.pfe.sageline.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "validation_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String parameter;

    @Column(name = "measured_value", nullable = false)
    private Double measuredValue;

    @Column(name = "expected_value", nullable = false)
    private Double expectedValue;

    @Column(nullable = false)
    private Boolean conform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
