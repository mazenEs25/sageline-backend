package com.pfe.sageline.dtos.internal;

import com.pfe.sageline.enums.MeasureStatus;

public record MandatoryCoverageRow(boolean mandatory, MeasureStatus status, long count) {}
