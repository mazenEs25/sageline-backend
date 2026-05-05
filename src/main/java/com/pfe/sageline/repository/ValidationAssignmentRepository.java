package com.pfe.sageline.repository;

import com.pfe.sageline.entity.ValidationAssignment;
import com.pfe.sageline.enums.AssignmentRole;
import com.pfe.sageline.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ValidationAssignmentRepository extends JpaRepository<ValidationAssignment, Long> {

    List<ValidationAssignment> findByValidationId(Long validationId);

    List<ValidationAssignment> findByUserId(Long userId);

    List<ValidationAssignment> findByUserIdAndStatus(Long userId, AssignmentStatus status);

    List<ValidationAssignment> findByValidationIdAndAssignmentRole(
            Long validationId, AssignmentRole role);

    List<ValidationAssignment> findByZoneId(Long zoneId);

    @Query("SELECT va FROM ValidationAssignment va " +
            "JOIN FETCH va.validation v " +
            "JOIN FETCH va.user u " +
            "JOIN FETCH va.zone z " +
            "WHERE va.validation.id = :validationId")
    List<ValidationAssignment> findByValidationIdWithDetails(
            @Param("validationId") Long validationId);

    @Query("SELECT va FROM ValidationAssignment va " +
            "JOIN FETCH va.validation v " +
            "WHERE va.user.id = :userId " +
            "AND v.status NOT IN ('CONFORME', 'NON_CONFORME', 'ANNULE')")
    List<ValidationAssignment> findActiveAssignmentsByUserId(
            @Param("userId") Long userId);

    @Query("SELECT COUNT(va) FROM ValidationAssignment va " +
            "WHERE va.validation.id = :validationId " +
            "AND va.assignmentRole = :role " +
            "AND va.status = 'TERMINE'")
    long countCompletedByValidationAndRole(
            @Param("validationId") Long validationId,
            @Param("role") AssignmentRole role);

    boolean existsByValidationIdAndUserIdAndAssignmentRole(
            Long validationId, Long userId, AssignmentRole role);

    /**
     * All assignments on a given ticket for a given user — used by
     * MessagingEventService to dedupe notification + auto-message when the
     * same tech is assigned to several postes of the same line-ticket.
     */
    List<ValidationAssignment> findByValidationIdAndUserId(Long validationId, Long userId);
}