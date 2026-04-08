package com.pfe.sageline.repository;
import com.pfe.sageline.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ValidationResultRepository extends JpaRepository<ValidationResult,Long> {
    List<ValidationResult> findByValidationId(Long validationId);

    List<ValidationResult> findByConform(Boolean conform);
    @Query("SELECT vr FROM ValidationResult vr WHERE vr.validation.id = :validationId AND vr.conform = :conform")
    List<ValidationResult> findByValidationIdAndConform(@Param("validationId") Long validationId, @Param("conform") Boolean conform);

    @Query("SELECT vr FROM ValidationResult vr WHERE vr.parameter = :parameter")
    List<ValidationResult> findByParameter(@Param("parameter") String parameter);

    @Query("SELECT COUNT(vr) FROM ValidationResult vr WHERE vr.validation.id = :validationId AND vr.conform = false")
    Long countNonConformByValidation(@Param("validationId") Long validationId);

    @Query("SELECT vr FROM ValidationResult vr WHERE vr.validation.validationZone.id = :zoneId")
    List<ValidationResult> findByValidationZoneId(@Param("zoneId") Long zoneId);




}
