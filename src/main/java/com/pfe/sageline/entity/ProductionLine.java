package com.pfe.sageline.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "productionLine", cascade = CascadeType.ALL)
    private List<ValidationZone> validationZones;

    @OneToMany(mappedBy = "productionLine", cascade = CascadeType.ALL)
    private List<KPI> kpis;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "line_number")
    private Integer lineNumber; // 1-11
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Phase phase;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
