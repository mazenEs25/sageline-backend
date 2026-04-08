package com.pfe.sageline.repository;
import com.pfe.sageline.entity.KPI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KPIRepository extends JpaRepository<KPI,Long> {
    List<KPI> findByProductionLineId(Long productionLineId);

    List<KPI> findByName(String name);
    @Query("SELECT k FROM KPI k WHERE k.productionLine.id = :lineId AND k.name = :name ORDER BY k.calculationDate DESC")
    List<KPI> findByLineAndName(@Param("lineId") Long lineId, @Param("name") String name);

    @Query("SELECT k FROM KPI k WHERE k.calculationDate BETWEEN :start AND :end")
    List<KPI> findByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT k FROM KPI k WHERE k.productionLine.id = :lineId AND k.calculationDate BETWEEN :start AND :end")
    List<KPI> findByLineAndDateRange(@Param("lineId") Long lineId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT k FROM KPI k WHERE k.productionLine.id = :lineId AND k.name = :name AND k.calculationDate = :date")
    Optional<KPI> findByLineNameAndDate(@Param("lineId") Long lineId, @Param("name") String name, @Param("date") LocalDate date);

    @Query("SELECT k FROM KPI k WHERE k.productionLine.id = :lineId ORDER BY k.calculationDate DESC")
    List<KPI> findLatestByLine(@Param("lineId") Long lineId);
}
