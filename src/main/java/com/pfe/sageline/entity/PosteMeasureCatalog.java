package com.pfe.sageline.entity;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "poste_measure_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PosteMeasureCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "poste_type", nullable = false, length = 64)
    private PosteType posteType;

    @Column(name = "measure_code", nullable = false, length = 64)
    private String measureCode;

    @Column(name = "measure_label", nullable = false, length = 255)
    private String measureLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeasureCategory category;

    @Column(name = "default_unit", nullable = false, length = 16)
    private String defaultUnit;

    @Column(name = "default_lower_bound", nullable = false)
    private Double defaultLowerBound;

    @Column(name = "default_upper_bound", nullable = false)
    private Double defaultUpperBound;

    @Column(nullable = false)
    private Boolean mandatory;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 16)
    private String antenna;

    @Column(name = "frequency_mhz")
    private Integer frequencyMhz;

    @Column(name = "modulation_scheme", length = 32)
    private String modulationScheme;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
