package com.pfe.sageline.dtos.response;

import java.util.List;

public record WorkflowReadinessDTO(
    Long ticketId,
    String currentStatus,
    String targetStatus,
    int mandatoryTotal,
    int mandatoryFilled,
    int mandatoryMissing,
    List<MissingMeasureDTO> missingMeasures,
    List<OutOfRangeMeasureDTO> outOfRangeMeasures,
    boolean canTransition,
    List<String> blockingReasons
) {}
