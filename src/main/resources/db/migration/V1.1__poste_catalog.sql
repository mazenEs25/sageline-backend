-- ============================================================================
-- V1.1__poste_catalog.sql
-- Create the PosteMeasureCatalog table with indexes and constraints.
-- ============================================================================

CREATE TABLE poste_measure_catalog (
    id                  BIGSERIAL PRIMARY KEY,
    poste_type          VARCHAR(64)              NOT NULL,
    measure_code        VARCHAR(64)              NOT NULL,
    measure_label       VARCHAR(255)             NOT NULL,
    category            VARCHAR(32)              NOT NULL,
    default_unit        VARCHAR(16)              NOT NULL,
    default_lower_bound DOUBLE PRECISION         NOT NULL,
    default_upper_bound DOUBLE PRECISION         NOT NULL,
    mandatory           BOOLEAN                  NOT NULL,
    display_order       INTEGER                  NOT NULL,
    active              BOOLEAN                  NOT NULL DEFAULT true,
    antenna             VARCHAR(16),
    frequency_mhz       INTEGER,
    modulation_scheme   VARCHAR(32),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by          BIGINT,                  -- nullable: seed/system inserts have no JWT actor
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by          BIGINT,                  -- nullable: same as above
    CONSTRAINT chk_poste_measure_catalog_bounds CHECK (default_lower_bound < default_upper_bound)
);

-- Partial unique index on active rows: a soft-deleted row may coexist with a new
-- active row sharing the same (poste_type, measure_code).
CREATE UNIQUE INDEX uk_poste_measure_catalog_active
    ON poste_measure_catalog (poste_type, measure_code)
    WHERE active = true;

-- Read-path index used by the by-poste-type + by-display-order queries.
CREATE INDEX idx_poste_measure_catalog_poste_type
    ON poste_measure_catalog (poste_type, active, display_order);

-- Lookup index for cross-poste code searches (e.g., importer alias resolution).
CREATE INDEX idx_poste_measure_catalog_code
    ON poste_measure_catalog (measure_code);
