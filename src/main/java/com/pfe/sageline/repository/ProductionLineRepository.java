package com.pfe.sageline.repository;
import com.pfe.sageline.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductionLineRepository extends JpaRepository<ProductionLine,Long> {
    Optional<ProductionLine> findByCode(String code);

    List<ProductionLine> findByActive(Boolean active);

    boolean existsByCode(String code);
    @Query("SELECT pl FROM ProductionLine pl LEFT JOIN FETCH pl.validationZones WHERE pl.id = :id")
    Optional<ProductionLine> findByIdWithZones(Long id);

    @Query("SELECT pl FROM ProductionLine pl LEFT JOIN FETCH pl.kpis WHERE pl.id = :id")
    Optional<ProductionLine> findByIdWithKpis(Long id);

    @Query("SELECT COUNT(vz) FROM ValidationZone vz WHERE vz.productionLine.id = :lineId")
    Long countZonesByLineId(Long lineId);
}
