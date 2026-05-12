package com.pfe.sageline.entity;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.MeasureStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "validation_measures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ValidationMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_template_id")
    private PosteMeasureCatalog catalogTemplate;

    @Column(name = "measure_code", nullable = false, length = 64)
    private String measureCode;

    @Column(name = "measure_label", nullable = false, length = 255)
    private String measureLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeasureCategory category;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "lower_bound", nullable = false)
    private Double lowerBound;

    @Column(name = "upper_bound", nullable = false)
    private Double upperBound;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeasureStatus status;

    @Column(name = "deviation_pct")
    private Double deviationPct;

    @Column(length = 16)
    private String antenna;

    @Column(name = "frequency_mhz")
    private Integer frequencyMhz;

    @Column(name = "modulation_scheme", length = 32)
    private String modulationScheme;

    @Column(name = "source_log_file", length = 255)
    private String sourceLogFile;

    @Column(name = "entered_by")
    private Long enteredBy;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
