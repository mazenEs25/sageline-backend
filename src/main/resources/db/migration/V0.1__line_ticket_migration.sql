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
-- Prerequisites
--   * Spring Boot must have been started at least ONCE after deploying the
--     2026-04 code. Because `spring.jpa.hibernate.ddl-auto=update`, Hibernate
--     adds the `production_line_id` column and creates the
--     `validation_poste_statuses` table automatically — this script only
--     populates them.
--   * Take a `pg_dump` backup first. Seriously. The workload is tiny but the
--     data is real production data.
--
-- Safety
--   * All writes run inside a single transaction. Re-run freely until happy.
--   * No DELETE / DROP anywhere.
--   * Set `v_dry_run := TRUE` to see what WOULD change without committing.
--
-- Usage
--   $ psql -h localhost -U postgres -d sageLine_db \
--          -f 2026-04-line-ticket-migration.sql
-- ============================================================================

\echo '=== 2026-04 line-ticket migration ==='

BEGIN;

-- ---------- Pre-flight sanity check -----------------------------------------
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
        RAISE EXCEPTION
          'Schema not ready. Start the Spring Boot app once so Hibernate creates validations.production_line_id and validation_poste_statuses, then re-run this migration.';
    END IF;
END$$;

-- ---------- Before snapshot -------------------------------------------------
\echo '--- Before ---'
SELECT
    COUNT(*)                                                          AS total_validations,
    COUNT(*) FILTER (WHERE production_line_id IS NOT NULL)            AS with_new_fk,
    COUNT(*) FILTER (WHERE production_line_id IS NULL)                AS without_new_fk,
    (SELECT COUNT(*) FROM validation_poste_statuses)                  AS poste_status_rows
FROM validations;

-- ============================================================================
-- STEP 1 — Backfill validations.production_line_id from the zone chain.
-- ============================================================================
WITH updated AS (
    UPDATE validations v
       SET production_line_id = vz.production_line_id
      FROM validation_zones vz
     WHERE v.validation_zone_id = vz.id
       AND v.production_line_id IS NULL
    RETURNING v.id
)
SELECT COUNT(*) AS validations_backfilled_with_line_fk FROM updated;

-- Any row still null after backfill has a broken legacy FK chain — report it.
SELECT id AS orphan_validation_id, ticket_code, validation_zone_id
  FROM validations
 WHERE production_line_id IS NULL;

-- ============================================================================
-- STEP 2 — Seed one validation_poste_statuses row per poste of each line
--          for every existing validation that doesn't already have poste rows.
-- ============================================================================
--
-- Status mapping:
--   * Terminal parents (CONFORME / NON_CONFORME / ANNULE) propagate their
--     status to every poste row (so historic KPIs stay coherent).
--   * Non-terminal parents (PLANIFIE, EN_ATTENTE_PREP, PREP_VALIDEE, EN_COURS,
--     EN_REVUE) get their postes seeded as PLANIFIE so the new per-poste
--     workflow can advance them individually.
--
-- If the validation has a primary zone set (validation_zone_id), that one
-- poste row is seeded with the parent status and the others as PLANIFIE — this
-- preserves the intuition that the poste the ticket was originally created
-- on is already "the one that's been worked on".

WITH seeds AS (
    SELECT
        v.id   AS validation_id,
        vz.id  AS zone_id,
        vz.order_in_line,
        CASE
            WHEN v.status IN ('CONFORME', 'NON_CONFORME', 'ANNULE')
                THEN v.status
            WHEN vz.id = v.validation_zone_id
                THEN v.status            -- primary poste mirrors parent
            ELSE 'PLANIFIE'              -- sibling postes start fresh
        END::varchar AS seeded_status
      FROM validations v
      JOIN validation_zones vz
        ON vz.production_line_id = COALESCE(
               v.production_line_id,
               (SELECT production_line_id FROM validation_zones
                 WHERE id = v.validation_zone_id))
      LEFT JOIN validation_poste_statuses existing
        ON existing.validation_id = v.id
     WHERE existing.id IS NULL       -- skip validations already migrated
),
inserted AS (
    INSERT INTO validation_poste_statuses
        (validation_id, zone_id, status, order_in_line, created_at, updated_at)
    SELECT
        s.validation_id,
        s.zone_id,
        s.seeded_status,
        s.order_in_line,
        NOW(), NOW()
      FROM seeds s
    ON CONFLICT ON CONSTRAINT uq_validation_poste DO NOTHING
    RETURNING id
)
SELECT COUNT(*) AS poste_status_rows_seeded FROM inserted;

-- ---------- After snapshot --------------------------------------------------
\echo '--- After ---'
SELECT
    COUNT(*)                                               AS total_validations,
    COUNT(*) FILTER (WHERE production_line_id IS NOT NULL) AS with_new_fk,
    COUNT(*) FILTER (WHERE production_line_id IS NULL)     AS still_without_fk,
    (SELECT COUNT(*) FROM validation_poste_statuses)       AS poste_status_rows
FROM validations;

-- Per-status breakdown of the freshly seeded poste rows
SELECT status, COUNT(*) AS rows
  FROM validation_poste_statuses
 GROUP BY status
 ORDER BY status;

-- ============================================================================
-- To preview (dry-run): replace `COMMIT;` with `ROLLBACK;` and re-run.
-- ============================================================================
COMMIT;

\echo '=== Migration done ==='
