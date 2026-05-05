package com.pfe.sageline.repository;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ValidationRepository extends JpaRepository<Validation,Long> {
    List<Validation> findByValidationZoneId(Long validationZoneId);
    List<Validation> findByValidationZoneIdAndStartDateAfter(Long zoneId, LocalDateTime after);
    List<Validation> findByStatus(TicketStatus status);

    List<Validation> findByCreatedById(Long userId);
    @Query("SELECT v FROM Validation v WHERE v.validationZone.id = :zoneId AND v.status = :status")
    List<Validation> findByZoneAndStatus(@Param("zoneId") Long zoneId, @Param("status") TicketStatus status);

    @Query("SELECT v FROM Validation v WHERE v.startDate BETWEEN :start AND :end")
    List<Validation> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT v FROM Validation v LEFT JOIN FETCH v.validationResults WHERE v.id = :id")
    Optional<Validation> findByIdWithResults(@Param("id") Long id);

    // Prefer the new direct FK (productionLine on Validation). Falls back to the
    // legacy chain (validationZone -> productionLine) for any unmigrated rows.
    @Query("SELECT v FROM Validation v " +
            "WHERE v.productionLine.id = :lineId " +
            "   OR (v.productionLine IS NULL AND v.validationZone.productionLine.id = :lineId)")
    List<Validation> findByProductionLineId(@Param("lineId") Long lineId);

    @Query("SELECT v FROM Validation v " +
            "WHERE (v.productionLine.id = :lineId " +
            "        OR (v.productionLine IS NULL AND v.validationZone.productionLine.id = :lineId)) " +
            "  AND v.status = :status")
    List<Validation> findByProductionLineIdAndStatus(@Param("lineId") Long lineId,
                                                     @Param("status") TicketStatus status);

    /**
     * Active (non-terminal) tickets for a given line on a given planned week.
     * Used to prevent creating duplicate line-level tickets for the same week.
     */
    @Query("SELECT v FROM Validation v " +
            "WHERE (v.productionLine.id = :lineId " +
            "        OR (v.productionLine IS NULL AND v.validationZone.productionLine.id = :lineId)) " +
            "  AND v.status NOT IN ('CONFORME', 'NON_CONFORME', 'ANNULE') " +
            "  AND ( (v.plannedWeekStart IS NOT NULL AND v.plannedWeekStart = :weekStart) " +
            "     OR (v.plannedWeekStart IS NULL AND v.plannedDate BETWEEN :weekStart AND :weekEnd) )")
    List<Validation> findActiveByLineAndWeek(@Param("lineId") Long lineId,
                                             @Param("weekStart") LocalDate weekStart,
                                             @Param("weekEnd") LocalDate weekEnd);

    @Query("SELECT COUNT(v) FROM Validation v WHERE v.status = :status AND v.startDate >= :date")
    Long countByStatusSinceDate(@Param("status") TicketStatus status, @Param("date") LocalDateTime date);

    @Query("SELECT v FROM Validation v WHERE v.status NOT IN ('CONFORME', 'NON_CONFORME', 'ANNULE') ORDER BY COALESCE(v.startDate, v.createdAt) ASC")
    List<Validation> findActiveValidations();
    //ticket
    @Query("SELECT v FROM Validation v WHERE v.plannedDate BETWEEN :start AND :end")
    List<Validation> findByPlannedDateBetween(
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Returns every validation planned inside the week [weekStart, weekEnd] (inclusive).
     * We filter by {@code plannedDate} instead of {@code plannedWeekStart/End} so that tickets
     * created through the single-ticket flow (which doesn't set the week range fields) also
     * show up on the weekly planner board.
     */
    @Query("SELECT v FROM Validation v " +
            "WHERE v.plannedDate BETWEEN :weekStart AND :weekEnd " +
            "   OR (v.plannedWeekStart IS NOT NULL AND v.plannedWeekEnd IS NOT NULL " +
            "       AND v.plannedWeekStart <= :weekEnd AND v.plannedWeekEnd >= :weekStart)")
    List<Validation> findByPlannedWeek(
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);

    @Query("SELECT v FROM Validation v " +
            "JOIN v.validationZone z " +
            "JOIN z.productionLine l " +
            "JOIN l.phase p " +
            "WHERE p.secteur.id = :secteurId")
    List<Validation> findBySecteurId(@Param("secteurId") Long secteurId);

    @Query("SELECT v FROM Validation v " +
            "JOIN v.assignments a " +
            "WHERE a.user.id = :userId " +
            "AND v.status NOT IN ('CONFORME', 'NON_CONFORME', 'ANNULE')")
    List<Validation> findActiveTicketsByUserId(@Param("userId") Long userId);

    @Query("SELECT v FROM Validation v " +
            "WHERE v.priority = 'URGENTE' " +
            "AND v.status NOT IN ('CONFORME', 'NON_CONFORME', 'ANNULE')")
    List<Validation> findUrgentActiveTickets();

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(v.ticketCode, 10) AS int)), 0) " +
            "FROM Validation v " +
            "WHERE v.ticketCode LIKE :prefix")
    int findMaxTicketNumber(@Param("prefix") String prefix);
    // Pour les KPIs
    @Query("SELECT COUNT(v) FROM Validation v " +
            "WHERE (v.productionLine.id = :lineId " +
            "        OR (v.productionLine IS NULL AND v.validationZone.productionLine.id = :lineId)) " +
            "  AND v.status = 'CONFORME' AND v.endDate >= :startDate")
    Long countConformeByLineAndDateRange(@Param("lineId") Long lineId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(v) FROM Validation v " +
            "WHERE (v.productionLine.id = :lineId " +
            "        OR (v.productionLine IS NULL AND v.validationZone.productionLine.id = :lineId)) " +
            "  AND v.status = 'NON_CONFORME' AND v.endDate >= :startDate")
    Long countNonConformeByLineAndDateRange(@Param("lineId") Long lineId, @Param("startDate") LocalDateTime startDate);
}

