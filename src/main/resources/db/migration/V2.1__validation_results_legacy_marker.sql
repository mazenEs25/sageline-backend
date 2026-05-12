-- Add audit column to legacy table before the data migration reads it as an idempotency gate.
ALTER TABLE validation_results ADD COLUMN IF NOT EXISTS migrated_at TIMESTAMP NULL;
