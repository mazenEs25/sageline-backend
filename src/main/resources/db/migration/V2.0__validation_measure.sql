CREATE TABLE validation_measures (
    id                   BIGSERIAL PRIMARY KEY,
    validation_id        BIGINT NOT NULL REFERENCES validations(id) ON DELETE CASCADE,
    catalog_template_id  BIGINT NULL REFERENCES poste_measure_catalog(id) ON DELETE SET NULL,
    measure_code         VARCHAR(64) NOT NULL,
    measure_label        VARCHAR(255) NOT NULL,
    category             VARCHAR(32) NOT NULL,
    unit                 VARCHAR(16) NOT NULL,
    lower_bound          DOUBLE PRECISION NOT NULL,
    upper_bound          DOUBLE PRECISION NOT NULL,
    measured_value       DOUBLE PRECISION NULL,
    status               VARCHAR(32) NOT NULL,
    deviation_pct        DOUBLE PRECISION NULL,
    antenna              VARCHAR(16) NULL,
    frequency_mhz        INTEGER NULL,
    modulation_scheme    VARCHAR(32) NULL,
    source_log_file      VARCHAR(255) NULL,
    entered_by           BIGINT NULL REFERENCES users(id),
    measured_at          TIMESTAMP NOT NULL,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP NOT NULL,
    CONSTRAINT ck_vm_bounds CHECK (lower_bound < upper_bound),
    CONSTRAINT ck_vm_deviation_consistency CHECK (
        (measured_value IS NULL  AND status = 'NOT_EXECUTED'           AND deviation_pct IS NULL) OR
        (measured_value IS NOT NULL AND status IN ('OK','OUT_OF_RANGE') AND deviation_pct IS NOT NULL)
    )
);
CREATE INDEX ix_vm_validation ON validation_measures(validation_id);
CREATE INDEX ix_vm_measure_code ON validation_measures(measure_code);
CREATE UNIQUE INDEX uq_vm_natural_key ON validation_measures(
    validation_id,
    measure_code,
    COALESCE(antenna, ''),
    COALESCE(frequency_mhz, -1),
    COALESCE(modulation_scheme, '')
);
