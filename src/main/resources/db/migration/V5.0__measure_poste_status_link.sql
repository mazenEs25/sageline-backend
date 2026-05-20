-- =============================================================================
-- V5.0 — Link validation_measures to validation_poste_statuses
-- =============================================================================
--
-- Background:
--   Until this migration, ValidationMeasure had no FK to a poste — it was attached
--   to the ticket as a whole. The "which poste does this measure belong to?" answer
--   was implicit, going through catalog_template.poste_type.
--
--   With the line-ticket model (one Validation = one ProductionLine, with one
--   ValidationPosteStatus per required poste of the line), we now want each measure
--   to explicitly belong to ONE posteStatus. This:
--     • lets per-poste UIs scope cleanly (filter by poste_status_id),
--     • lets the per-poste "Clôturer" button validate ITS measures,
--     • lets the line-wide auto-transition (all postes terminal → EN_REVUE) fire
--       on accurate per-poste counts.
--
-- Strategy:
--   1. Add nullable column + index + FK.
--   2. Backfill — catalog-linked measures via catalog_template.poste_type match;
--      ad-hoc measures via the legacy validation_zone_id on the parent ticket.
--   3. Recreate the natural-key uniqueness index to include poste_status_id, so two
--      different postes on the same line can legitimately carry the same measureCode
--      (e.g. each poste has a "TEMPS_TEST" without colliding).
--
--   Column is left NULLABLE for now. A follow-up migration can ALTER … SET NOT NULL
--   once we've confirmed no row escaped backfill in any environment AND the new
--   write paths reliably populate it.
-- =============================================================================

-- 1. Add the column + FK
ALTER TABLE validation_measures
    ADD COLUMN poste_status_id BIGINT NULL
        REFERENCES validation_poste_statuses(id) ON DELETE SET NULL;

-- 2a. Backfill — catalog-linked measures.
--     For each measure with a catalog_template, find the posteStatus on the same
--     validation whose zone.poste_type matches the template's poste_type.
UPDATE validation_measures m
SET poste_status_id = ps.id
FROM validation_poste_statuses ps
JOIN validation_zones vz       ON ps.zone_id    = vz.id
JOIN poste_measure_catalog pmc ON pmc.poste_type = vz.poste_type
WHERE m.validation_id     = ps.validation_id
  AND m.catalog_template_id = pmc.id
  AND m.poste_status_id IS NULL;

-- 2b. Backfill — ad-hoc measures (no catalog template).
--     Fall back to the legacy validation_zone_id on the parent ticket, which during
--     the line-ticket migration was populated with the FIRST poste of the line.
--     This is the best deterministic guess we can make for measures that never
--     declared a poste.
UPDATE validation_measures m
SET poste_status_id = ps.id
FROM validations v
JOIN validation_poste_statuses ps ON ps.validation_id = v.id
WHERE m.validation_id        = v.id
  AND m.catalog_template_id IS NULL
  AND ps.zone_id             = v.validation_zone_id
  AND m.poste_status_id     IS NULL;

-- 3. Per-poste lookups will be the common access pattern.
CREATE INDEX ix_vm_poste_status ON validation_measures(poste_status_id);

-- 4. Recreate the natural-key uniqueness to include poste_status_id.
--    Two different postes can legitimately carry the same measure code (e.g. each
--    poste has its own TEMPS_TEST). COALESCE(poste_status_id, -1) keeps NULLs from
--    making the index unusable while the data is still being migrated.
DROP INDEX IF EXISTS uq_vm_natural_key;
CREATE UNIQUE INDEX uq_vm_natural_key ON validation_measures(
    validation_id,
    COALESCE(poste_status_id, -1),
    measure_code,
    COALESCE(antenna, ''),
    COALESCE(frequency_mhz, -1),
    COALESCE(modulation_scheme, '')
);

-- 5. Optional sanity assertion: log how many rows are still without a posteStatus.
--    This shows up in the Flyway migration log and helps catch backfill gaps.
DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM validation_measures
    WHERE poste_status_id IS NULL;

    IF orphan_count > 0 THEN
        RAISE NOTICE 'V5.0 backfill: % validation_measures rows still have poste_status_id = NULL. '
                     'Likely tickets created before the line-ticket migration with no validation_zone_id, '
                     'or measures whose catalog template was soft-deleted. Inspect with: '
                     'SELECT m.id, m.validation_id, m.measure_code, m.catalog_template_id '
                     'FROM validation_measures m WHERE m.poste_status_id IS NULL;',
                     orphan_count;
    END IF;
END $$;
