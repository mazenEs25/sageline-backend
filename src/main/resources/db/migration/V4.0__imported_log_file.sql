-- Phase 004: Sagemcom Log Importer
-- Create imported_log_file table and extend validation_measures

-- Create imported_log_file table
CREATE TABLE imported_log_file (
    id BIGSERIAL PRIMARY KEY,
    validation_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    detected_format VARCHAR(32) NOT NULL,
    stored_path VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes <= 2097152),
    uploaded_by BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    overwrite_existing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_imported_log_file_validation FOREIGN KEY (validation_id) REFERENCES validations(id) ON DELETE CASCADE,
    CONSTRAINT fk_imported_log_file_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

-- Create indexes
CREATE INDEX idx_imported_log_file_validation_id ON imported_log_file (validation_id, uploaded_at DESC);

-- Add columns to validation_measures
ALTER TABLE validation_measures
ADD COLUMN imported_log_file_id BIGINT NULL,
ADD COLUMN source_declared_status VARCHAR(32) NULL,
ADD CONSTRAINT fk_validation_measure_imported_log_file FOREIGN KEY (imported_log_file_id) REFERENCES imported_log_file(id) ON DELETE SET NULL,
ADD CONSTRAINT chk_validation_measure_source_declared_status CHECK (source_declared_status IS NULL OR source_declared_status IN ('OK_FROM_LOG', 'OUT_OF_RANGE_FROM_LOG', 'NOT_EXECUTED_FROM_LOG'));

-- Create index for snippet retrieval
CREATE INDEX idx_validation_measure_imported_log_file ON validation_measures (imported_log_file_id);
