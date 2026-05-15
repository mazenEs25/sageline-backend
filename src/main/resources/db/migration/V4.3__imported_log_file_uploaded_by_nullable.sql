-- ============================================================================
-- V4.3 — Relax imported_log_file.uploaded_by from NOT NULL to NULL.
--
-- Why: SecurityUtils.getCurrentUserId() returns null until the Keycloak user
-- has called GET /api/users/me once (it looks up the local user by Keycloak
-- subject id). For first-time imports — and for all integration tests where
-- the JWT is mocked — uploaded_by must be allowed to be null. The migration
-- chain previously had NOT NULL on this column; this migration loosens it
-- forward-only so already-applied V4.0 checksums stay intact.
-- ============================================================================

ALTER TABLE imported_log_file ALTER COLUMN uploaded_by DROP NOT NULL;
