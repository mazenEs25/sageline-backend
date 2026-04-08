package com.pfe.sageline.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kpis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KPI {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double value;

    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_line_id")
    private ProductionLine productionLine;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (calculationDate == null) {
            calculationDate = LocalDate.now();
        }
    }

}
