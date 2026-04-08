package com.pfe.sageline.repository;
import com.pfe.sageline.entity.ValidationZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ValidationZoneRepository extends JpaRepository<ValidationZone,Long> {
    List<ValidationZone> findByProductionLineId(Long productionLineId);

    Optional<ValidationZone> findByNameAndProductionLineId(String name, Long productionLineId);
    @Query("SELECT vz FROM ValidationZone vz LEFT JOIN FETCH vz.validations WHERE vz.id = :id")
    Optional<ValidationZone> findByIdWithValidations(@Param("id") Long id);

    @Query("SELECT COUNT(v) FROM Validation v WHERE v.validationZone.id = :zoneId")
    Long countValidationsByZoneId(@Param("zoneId") Long zoneId);

    boolean existsByNameAndProductionLineId(String name, Long productionLineId);

}
