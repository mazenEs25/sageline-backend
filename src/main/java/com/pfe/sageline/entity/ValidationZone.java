package com.pfe.sageline.entity;
import com.pfe.sageline.enums.PosteType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Builder
@Entity
@Table(name = "validation_zones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationZone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "poste_type")
    private PosteType posteType;

    @Column(name = "order_in_line")
    private Integer orderInLine; // Order of this poste in the production line

    @Column(name = "requires_tool_check")
    @Builder.Default
    private Boolean requiresToolCheck = true; // Whether TECH_PREP must verify tooling

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_line_id", nullable = false)
    private ProductionLine productionLine;

    @OneToMany(mappedBy = "validationZone", cascade = CascadeType.ALL)
    private List<Validation> validations;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
