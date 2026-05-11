-- ============================================================================
-- V1.3__validation_zone_nullable.sql
-- Align validations.validation_zone_id with the entity: the Validation entity
-- treats the zone relation as optional (@ManyToOne @JoinColumn(name="validation_zone_id")
-- with no nullable=false). The column was historically NOT NULL because the
-- pre-refactor model required exactly one zone per ticket; that requirement
-- was dropped during the line-ticket migration (see V0.1) but the column
-- constraint was never relaxed. ddl-auto=validate now catches the drift.
-- ============================================================================

ALTER TABLE validations
    ALTER COLUMN validation_zone_id DROP NOT NULL;
