-- ============================================================================
-- 2026-04 Sagemcom line-ticket migration
-- ----------------------------------------------------------------------------
-- Purpose
--   Before 2026-04, one Validation ticket covered ONE ValidationZone (poste).
--   After the Sagemcom supervisor decision, one ticket covers an entire
--   ProductionLine and each required poste of that line gets its own
--   ValidationPosteStatus sub-row.
--
-- What this script does
--   1. Backfills the new `validations.production_line_id` FK from the legacy
--      `validation_zone -> production_line` chain. Idempotent.
--   2. Seeds `validation_poste_statuses` rows for every existing validation,
--      mirroring the parent ticket's current status. Idempotent (skips
--      tickets that already have poste rows).
--
-- When run via Flyway on a fresh schema (columns/tables not yet present),
-- the script detects the missing schema and exits with a NOTICE — no-op.
-- ============================================================================

DO $$
DECLARE
    has_line_fk   BOOLEAN;
    has_poste_tbl BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'validations' AND column_name = 'production_line_id'
    ) INTO has_line_fk;

    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'validation_poste_statuses'
    ) INTO has_poste_tbl;

    IF NOT has_line_fk OR NOT has_poste_tbl THEN
        RAISE NOTICE 'V0.1 skipped: schema not ready (has_line_fk=%, has_poste_tbl=%)',
                     has_line_fk, has_poste_tbl;
        RETURN;
    END IF;

    -- STEP 1: Backfill validations.production_line_id from the zone chain.
    UPDATE validations v
       SET production_line_id = vz.production_line_id
      FROM validation_zones vz
     WHERE v.validation_zone_id = vz.id
       AND v.production_line_id IS NULL;

    -- STEP 2: Seed one validation_poste_statuses row per poste of each line
    --         for every existing validation that doesn't already have poste rows.
    INSERT INTO validation_poste_statuses
        (validation_id, zone_id, status, order_in_line, created_at, updated_at)
    SELECT
        v.id,
        vz.id,
        CASE
            WHEN v.status IN ('CONFORME', 'NON_CONFORME', 'ANNULE')
                THEN v.status
            WHEN vz.id = v.validation_zone_id
                THEN v.status
            ELSE 'PLANIFIE'
        END::varchar,
        vz.order_in_line,
        NOW(), NOW()
      FROM validations v
      JOIN validation_zones vz
        ON vz.production_line_id = COALESCE(
               v.production_line_id,
               (SELECT production_line_id FROM validation_zones WHERE id = v.validation_zone_id))
      LEFT JOIN validation_poste_statuses existing ON existing.validation_id = v.id
     WHERE existing.id IS NULL
    ON CONFLICT ON CONSTRAINT uq_validation_poste DO NOTHING;

END$$;
