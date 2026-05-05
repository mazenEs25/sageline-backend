package com.pfe.sageline.repository;

import com.pfe.sageline.entity.ValidationPosteStatus;
import com.pfe.sageline.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationPosteStatusRepository extends JpaRepository<ValidationPosteStatus, Long> {

    @Query("SELECT p FROM ValidationPosteStatus p " +
            "LEFT JOIN FETCH p.zone " +
            "LEFT JOIN FETCH p.validatedBy " +
            "WHERE p.validation.id = :validationId " +
            "ORDER BY COALESCE(p.orderInLine, 0), p.id")
    List<ValidationPosteStatus> findByValidationIdOrdered(@Param("validationId") Long validationId);

    Optional<ValidationPosteStatus> findByValidationIdAndZoneId(Long validationId, Long zoneId);

    long countByValidationId(Long validationId);

    long countByValidationIdAndStatus(Long validationId, TicketStatus status);

    long countByValidationIdAndStatusIn(Long validationId, List<TicketStatus> statuses);
}
