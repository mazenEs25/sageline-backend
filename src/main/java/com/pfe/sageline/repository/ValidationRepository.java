package com.pfe.sageline.repository;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ValidationRepository extends JpaRepository<Validation,Long> {
    List<Validation> findByValidationZoneId(Long validationZoneId);
    List<Validation> findByValidationZoneIdAndStartDateAfter(Long zoneId, LocalDateTime after);
    List<Validation> findByStatus(ValidationStatus status);
    @Query("SELECT v FROM Validation v WHERE v.validationZone.id = :zoneId AND v.status = :status")
    List<Validation> findByZoneAndStatus(@Param("zoneId") Long zoneId, @Param("status") ValidationStatus status);

    @Query("SELECT v FROM Validation v WHERE v.startDate BETWEEN :start AND :end")
    List<Validation> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT v FROM Validation v LEFT JOIN FETCH v.validationResults WHERE v.id = :id")
    Optional<Validation> findByIdWithResults(@Param("id") Long id);

    @Query("SELECT v FROM Validation v WHERE v.validationZone.productionLine.id = :lineId")
    List<Validation> findByProductionLineId(@Param("lineId") Long lineId);

    @Query("SELECT v FROM Validation v WHERE v.validationZone.productionLine.id = :lineId AND v.status = :status")
    List<Validation> findByProductionLineIdAndStatus(@Param("lineId") Long lineId, @Param("status") ValidationStatus status);

    @Query("SELECT COUNT(v) FROM Validation v WHERE v.status = :status AND v.startDate >= :date")
    Long countByStatusSinceDate(@Param("status") ValidationStatus status, @Param("date") LocalDateTime date);

    @Query("SELECT v FROM Validation v WHERE v.status = 'EN_COURS' ORDER BY v.startDate ASC")
    List<Validation> findActiveValidations();

    // Pour les KPIs
    @Query("SELECT COUNT(v) FROM Validation v WHERE v.validationZone.productionLine.id = :lineId AND v.status = 'CONFORME' AND v.endDate >= :startDate")
    Long countConformeByLineAndDateRange(@Param("lineId") Long lineId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(v) FROM Validation v WHERE v.validationZone.productionLine.id = :lineId AND v.status = 'NON_CONFORME' AND v.endDate >= :startDate")
    Long countNonConformeByLineAndDateRange(@Param("lineId") Long lineId, @Param("startDate") LocalDateTime startDate);
}
