-- ============================================================================
-- 2026-05 Handover status constraints migration
-- ----------------------------------------------------------------------------
-- Purpose
--   The Handover (Passation) feature added two new enum values that the
--   existing PostgreSQL CHECK constraints do not allow yet:
--     * AssignmentStatus.PAUSED  -> validation_assignments.status
--     * TicketStatus.EN_ATTENTE_HANDOVER -> validations.status
--
--   Hibernate's `ddl-auto=update` creates new columns and tables but does NOT
--   modify existing CHECK constraints, so attempts to write the new values
--   fail with: "ERREUR: la nouvelle ligne ... viole la contrainte de
--   verification 'validation_assignments_status_check'".
--
--   This script drops and recreates each constraint with the full enum set.
--
-- Prerequisites
--   * Spring Boot must have started at least once with the 2026-05 code so
--     the enum classes are loaded (Hibernate touches them).
--   * Take a pg_dump first. The workload is tiny but the data is real.
--
-- Safety
--   * Idempotent: each block looks up the constraint name dynamically and
--     skips if not found, then recreates.
--   * No DELETE / DROP TABLE anywhere — only DROP CONSTRAINT + ADD CONSTRAINT.
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. validation_assignments.status  (add PAUSED)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_constraint_name TEXT;
BEGIN
    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validation_assignments'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validation_assignments DROP CONSTRAINT %I', v_constraint_name);
        RAISE NOTICE 'Dropped constraint % on validation_assignments', v_constraint_name;
    END IF;

    ALTER TABLE validation_assignments
        ADD CONSTRAINT validation_assignments_status_check
        CHECK (status IN ('ASSIGNE', 'EN_COURS', 'PAUSED', 'TERMINE'));

    RAISE NOTICE 'Recreated validation_assignments_status_check with PAUSED';
END $$;

-- ---------------------------------------------------------------------------
-- 2. validations.status  (add EN_ATTENTE_HANDOVER)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_constraint_name TEXT;
BEGIN
    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validations'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validations DROP CONSTRAINT %I', v_constraint_name);
        RAISE NOTICE 'Dropped constraint % on validations', v_constraint_name;
    END IF;

    ALTER TABLE validations
        ADD CONSTRAINT validations_status_check
        CHECK (status IN (
            'PLANIFIE',
            'EN_ATTENTE_PREP',
            'PREP_VALIDEE',
            'EN_COURS',
            'EN_ATTENTE_HANDOVER',
            'EN_REVUE',
            'CONFORME',
            'NON_CONFORME',
            'ANNULE'
        ));

    RAISE NOTICE 'Recreated validations_status_check with EN_ATTENTE_HANDOVER';
END $$;

-- ---------------------------------------------------------------------------
-- 3. (Optional) validation_poste_statuses.status — same pattern, in case the
--    poste sub-status table also has a CHECK that lists the old enum subset.
--    Skipped if the table doesn't exist or has no status CHECK.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_table_exists BOOLEAN;
    v_constraint_name TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'validation_poste_statuses'
    ) INTO v_table_exists;

    IF NOT v_table_exists THEN
        RAISE NOTICE 'Skipping validation_poste_statuses — table not present';
        RETURN;
    END IF;

    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validation_poste_statuses'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validation_poste_statuses DROP CONSTRAINT %I', v_constraint_name);
        RAISE NOTICE 'Dropped constraint % on validation_poste_statuses', v_constraint_name;

        ALTER TABLE validation_poste_statuses
            ADD CONSTRAINT validation_poste_statuses_status_check
            CHECK (status IN (
                'PLANIFIE',
                'EN_ATTENTE_PREP',
                'PREP_VALIDEE',
                'EN_COURS',
                'EN_ATTENTE_HANDOVER',
                'EN_REVUE',
                'CONFORME',
                'NON_CONFORME',
                'ANNULE'
            ));

        RAISE NOTICE 'Recreated validation_poste_statuses_status_check with EN_ATTENTE_HANDOVER';
    END IF;
END $$;

COMMIT;

-- ============================================================================
-- Verification queries — run these after the COMMIT to confirm
-- ============================================================================
-- SELECT conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid IN ('validation_assignments'::regclass, 'validations'::regclass)
--   AND contype = 'c';
