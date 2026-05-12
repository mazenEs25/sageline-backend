package com.pfe.sageline.service;

import com.pfe.sageline.enums.MeasureStatus;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for measure status and deviation percentage.
 *
 * <p>Canonical SC-001 fixtures (used in unit tests):
 * <ul>
 *   <li>{@code 15.5} on {@code [13.5, 16.5]} → {@code OK}, deviation ≈ 33.33</li>
 *   <li>{@code 20.0} on {@code [13.5, 16.5]} → {@code OUT_OF_RANGE}, deviation ≈ 333.33</li>
 *   <li>{@code null} → {@code NOT_EXECUTED}, deviation {@code null}</li>
 * </ul>
 */
@Component
public class MeasureDeviationCalculator {

    public MeasureStatus computeStatus(Double measured, double lowerBound, double upperBound) {
        if (measured == null) {
            return MeasureStatus.NOT_EXECUTED;
        }
        validateBounds(lowerBound, upperBound);
        return (measured >= lowerBound && measured <= upperBound)
                ? MeasureStatus.OK
                : MeasureStatus.OUT_OF_RANGE;
    }

    public Double computeDeviationPct(Double measured, double lowerBound, double upperBound) {
        if (measured == null) {
            return null;
        }
        validateBounds(lowerBound, upperBound);
        double center = (lowerBound + upperBound) / 2.0;
        double halfRange = (upperBound - lowerBound) / 2.0;
        return Math.abs(measured - center) / halfRange * 100.0;
    }

    private void validateBounds(double lowerBound, double upperBound) {
        if (upperBound <= lowerBound) {
            throw new IllegalStateException("halfRange must be > 0");
        }
    }
}
