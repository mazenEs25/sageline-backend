package com.pfe.sageline.repository;

import com.pfe.sageline.entity.ToolRecommendation;
import com.pfe.sageline.enums.ToolStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ToolRecommendationRepository extends JpaRepository<ToolRecommendation,Long> {
    List<ToolRecommendation> findByStatus(ToolStatus status);

    @Query("SELECT t FROM ToolRecommendation t WHERE t.status = 'DISPONIBLE' " +
            "AND (t.compatibleZones LIKE %:zoneId% OR t.compatibleZones IS NULL)")
    List<ToolRecommendation> findAvailableForZone(@Param("zoneId") String zoneId);

    @Query("SELECT t FROM ToolRecommendation t WHERE t.status = 'DISPONIBLE' " +
            "AND (t.compatibleLines LIKE %:lineId% OR t.compatibleLines IS NULL)")
    List<ToolRecommendation> findAvailableForLine(@Param("lineId") String lineId);
}

