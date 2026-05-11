package com.pfe.sageline.enums;

/**
 * Three-valued outcome state of a measure on a validation ticket.
 *
 * Mapping to Sagemcom Status codes:
 * | Status      | Sagemcom Code | Meaning                                           |
 * |-------------|---------------|---------------------------------------------------|
 * | OK          | 0             | Measure was executed and result is within bounds |
 * | OUT_OF_RANGE| 1             | Measure was executed but result exceeds bounds   |
 * | NOT_EXECUTED| 2             | Measure was not executed (skipped or blocked)    |
 */
public enum MeasureStatus {
    OK,
    OUT_OF_RANGE,
    NOT_EXECUTED
}
