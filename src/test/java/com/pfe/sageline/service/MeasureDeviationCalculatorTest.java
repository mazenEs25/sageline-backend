package com.pfe.sageline.service;

import com.pfe.sageline.enums.MeasureStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MeasureDeviationCalculatorTest {

    private final MeasureDeviationCalculator calc = new MeasureDeviationCalculator();

    @Test
    void computesOkAndDeviationForCenterFixture() {
        assertThat(calc.computeStatus(15.5, 13.5, 16.5)).isEqualTo(MeasureStatus.OK);
        assertThat(calc.computeDeviationPct(15.5, 13.5, 16.5)).isCloseTo(33.33, within(0.1));
    }

    @Test
    void computesOutOfRangeForFixtureTwo() {
        assertThat(calc.computeStatus(20.0, 13.5, 16.5)).isEqualTo(MeasureStatus.OUT_OF_RANGE);
        assertThat(calc.computeDeviationPct(20.0, 13.5, 16.5)).isCloseTo(333.33, within(0.5));
    }

    @Test
    void nullMeasuredYieldsNotExecuted() {
        assertThat(calc.computeStatus(null, 13.5, 16.5)).isEqualTo(MeasureStatus.NOT_EXECUTED);
        assertThat(calc.computeDeviationPct(null, 13.5, 16.5)).isNull();
    }

    @Test
    void boundaryValueIsOk() {
        assertThat(calc.computeStatus(13.5, 13.5, 16.5)).isEqualTo(MeasureStatus.OK);
        assertThat(calc.computeDeviationPct(13.5, 13.5, 16.5)).isCloseTo(100.0, within(0.01));
    }

    @Test
    void zeroWidthThrows() {
        assertThatThrownBy(() -> calc.computeStatus(5.0, 4.0, 4.0))
                .isInstanceOf(IllegalStateException.class);
    }
}
