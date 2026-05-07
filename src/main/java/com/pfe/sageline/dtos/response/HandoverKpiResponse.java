package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.TriggerType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record HandoverKpiResponse(
        LocalDate from,
        LocalDate to,
        long totalCount,
        Map<TriggerType, Long> byTriggerType,
        List<ZoneCount> byZone,
        List<TechnicianCount> byTechnician,
        TimeToAcceptStats timeToAccept
) {
    public record ZoneCount(Long zoneId, String zoneName, long count) {}
    public record TechnicianCount(Long userId, String username, long count) {}
    public record TimeToAcceptStats(long sampleSize, long medianSeconds, long p95Seconds) {}
}
