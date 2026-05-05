package com.pfe.sageline.entity;

import com.pfe.sageline.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks the validation state of a single poste (ValidationZone) inside a
 * line-level Validation ticket.
 *one Validation covers an
 * entire ProductionLine rather than a single poste. Each required poste of
 * the line gets exactly one ValidationPosteStatus row, letting different
 * techs complete different postes independently while the ticket itself
 * stays a single unit until every poste is closed.
 */
@Entity
@Table(
        name = "validation_poste_statuses",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_validation_poste",
                columnNames = {"validation_id", "zone_id"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPosteStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Validation validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ValidationZone zone;

    /**
     * Reuses the TicketStatus enum. Typical lifecycle per poste:
     * PLANIFIE → EN_COURS → CONFORME | NON_CONFORME.
     * The surrounding Validation goes to EN_REVUE automatically once
     * every poste reaches a terminal state.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private TicketStatus status = TicketStatus.PLANIFIE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User validatedBy;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Display order within the line — copied from ValidationZone.orderInLine at seed time. */
    @Column(name = "order_in_line")
    private Integer orderInLine;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
