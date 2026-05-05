package com.pfe.sageline.entity;

import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.enums.Priority;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "validations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Validation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== NEW: Ticket code =====
    @Column(name = "ticket_code", unique = true, nullable = false, length = 20)
    private String ticketCode; // Auto-generated: VAL-2026-0001

    // ===== MODIFIED: TicketStatus replaces ValidationStatus =====
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TicketStatus status = TicketStatus.PLANIFIE;

    // ===== LINE-LEVEL TICKET (new canonical relationship) =====
    // Since 2026-04, one ticket = one production line. Postes live in
    // `posteStatuses` below (ValidationPosteStatus sub-entity).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_line_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ProductionLine productionLine;

    // ===== LEGACY: Zone relationship — now nullable =====
    // Kept for backward compat with AI prediction, KPI queries and the
    // legacy zone-scoped dashboards. For new tickets we populate it with the
    // first poste of the line so the existing code keeps working while we
    // migrate the call-sites one by one.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_zone_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ValidationZone validationZone;

    // ===== NEW: Who created this ticket =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User createdBy;

    // ===== NEW: Planning fields =====
    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "planned_week_start")
    private LocalDate plannedWeekStart;

    @Column(name = "planned_week_end")
    private LocalDate plannedWeekEnd;

    // ===== EXISTING: Dates (unchanged) =====
    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    // ===== NEW: Priority =====
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.NORMALE;

    // ===== EXISTING + NEW: Comments =====
    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "prep_comments", columnDefinition = "TEXT")
    private String prepComments; // TECH_PREP comments on tooling

    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments; // CHEF/EXPERT review comments

    // ===== NEW: Tool verification =====
    @Column(name = "tools_verified")
    @Builder.Default
    private Boolean toolsVerified = false;

    @Column(name = "tools_verified_at")
    private LocalDateTime toolsVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tools_verified_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User toolsVerifiedBy;

    // ===== EXISTING: Results relationship (unchanged) =====
    @OneToMany(mappedBy = "validation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ValidationResult> validationResults = new ArrayList<>();

    // ===== NEW: Assignments relationship =====
    @OneToMany(mappedBy = "validation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ValidationAssignment> assignments = new ArrayList<>();

    // ===== NEW: Per-poste sub-status rows (one per required poste of the line) =====
    @OneToMany(mappedBy = "validation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ValidationPosteStatus> posteStatuses = new ArrayList<>();

    // ===== EXISTING: AI Prediction (unchanged) =====
    @OneToOne(mappedBy = "validation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private NonConformityPrediction prediction;

    // ===== Timestamps =====
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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