package com.pfe.sageline.dtos.response;

public record OutOfRangeMeasureDTO(String measureCode, String label, Double measuredValue,
                                   Double lowerBound, Double upperBound, Double deviationPct) {}
