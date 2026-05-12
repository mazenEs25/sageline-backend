-- Migrate legacy validation_results rows into validation_measures (idempotent via migrated_at IS NULL guard).
INSERT INTO validation_measures (
    validation_id, catalog_template_id, measure_code, measure_label, category,
    unit, lower_bound, upper_bound, measured_value, status, deviation_pct,
    antenna, frequency_mhz, modulation_scheme, source_log_file,
    entered_by, measured_at, created_at, updated_at
)
SELECT
    vr.validation_id,
    NULL,
    UPPER(REGEXP_REPLACE(vr.parameter, '[^A-Za-z0-9]', '_', 'g')),
    vr.parameter,
    'OTHER',
    'unknown',
    -- LEAST/GREATEST ensures lower < upper even when expected_value is negative
    -- (e.g. dBm values: -50 * 0.95 = -47.5 > -50 * 1.05 = -52.5 without LEAST)
    LEAST(
        CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
        CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
    ),
    GREATEST(
        CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
        CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
    ),
    vr.measured_value,
    -- ck_vm_deviation_consistency forbids non-NOT_EXECUTED status with NULL measured_value;
    -- guard here even though legacy schema currently has measured_value NOT NULL.
    CASE
      WHEN vr.measured_value IS NULL THEN 'NOT_EXECUTED'
      WHEN vr.conform                 THEN 'OK'
      ELSE                                 'OUT_OF_RANGE'
    END,
    CASE
      WHEN vr.measured_value IS NULL THEN NULL
      ELSE ABS(
               vr.measured_value
               - (LEAST(
                      CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
                      CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
                  )
                + GREATEST(
                      CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
                      CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
                  )) / 2
           ) /
           ((GREATEST(
                CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
                CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
            )
            - LEAST(
                CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
                CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END
            )) / 2) * 100
    END,
    NULL, NULL, NULL, NULL,
    NULL,
    COALESCE(vr.created_at, NOW()),
    COALESCE(vr.created_at, NOW()),
    NOW()
FROM validation_results vr
WHERE vr.migrated_at IS NULL;

-- Stamp migrated rows so re-runs are idempotent.
UPDATE validation_results vr
SET    migrated_at = NOW()
WHERE  migrated_at IS NULL
  AND  EXISTS (
         SELECT 1 FROM validation_measures vm
         WHERE  vm.validation_id = vr.validation_id
           AND  vm.measure_code   = UPPER(REGEXP_REPLACE(vr.parameter, '[^A-Za-z0-9]', '_', 'g'))
       );
