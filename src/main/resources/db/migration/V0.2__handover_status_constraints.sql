-- ============================================================================
-- 2026-05 Handover status constraints migration
-- ----------------------------------------------------------------------------
-- Drops and recreates CHECK constraints on validation_assignments and
-- validations to include the new enum values PAUSED and EN_ATTENTE_HANDOVER.
-- When run via Flyway on a fresh schema, each block detects missing tables
-- and exits with a NOTICE — no-op.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. validation_assignments.status  (add PAUSED)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_table_exists    BOOLEAN;
    v_constraint_name TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'validation_assignments'
    ) INTO v_table_exists;

    IF NOT v_table_exists THEN
        RAISE NOTICE 'V0.2 block 1 skipped: validation_assignments not present';
        RETURN;
    END IF;

    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validation_assignments'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validation_assignments DROP CONSTRAINT %I', v_constraint_name);
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
    v_table_exists    BOOLEAN;
    v_constraint_name TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'validations'
    ) INTO v_table_exists;

    IF NOT v_table_exists THEN
        RAISE NOTICE 'V0.2 block 2 skipped: validations not present';
        RETURN;
    END IF;

    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validations'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validations DROP CONSTRAINT %I', v_constraint_name);
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
-- 3. validation_poste_statuses.status (optional)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_table_exists    BOOLEAN;
    v_constraint_name TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'validation_poste_statuses'
    ) INTO v_table_exists;

    IF NOT v_table_exists THEN
        RAISE NOTICE 'V0.2 block 3 skipped: validation_poste_statuses not present';
        RETURN;
    END IF;

    SELECT conname INTO v_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'validation_poste_statuses'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE validation_poste_statuses DROP CONSTRAINT %I', v_constraint_name);

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
