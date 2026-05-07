package com.pfe.sageline.entity;

import com.pfe.sageline.enums.HandoverStatus;
import com.pfe.sageline.enums.TriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_handovers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Validation validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_tech_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User fromTech;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_tech_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User toTech;

    @Column(columnDefinition = "TEXT")
    private String handoverNote;

    @Column(columnDefinition = "TEXT")
    private String progressSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private HandoverStatus status = HandoverStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggeredBy;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
}
