package com.pfe.sageline.service.Import;

import com.pfe.sageline.exception.ImportInProgressException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages Postgres advisory locks for log imports.
 * Ensures only one import per validation runs at a time.
 *
 * <p><b>Must be called from an active transaction</b> (Propagation.MANDATORY).
 * The advisory lock is session-scoped: if the connection is returned to the pool
 * before the import finishes, the lock is released and the invariant is broken.
 * The caller (LogImportService) opens the transaction; the lock then lives until
 * commit/rollback. {@link LockHandle#close()} also runs pg_advisory_unlock as a
 * best-effort early release; failures are logged and swallowed because the session
 * close will release the lock regardless.
 */
@Service
@Slf4j
public class ImportLockService {

    private final JdbcTemplate jdbcTemplate;

    public ImportLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Attempts to acquire an advisory lock for the given validation ID.
     * Throws ImportInProgressException if another import holds the lock.
     * Returns an AutoCloseable LockHandle that releases the lock on close.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockHandle tryAcquire(Long validationId) {
        // pg_try_advisory_lock has two signatures: (bigint) and (integer, integer).
        // We use the two-integer form for the namespace+id pattern, so the validationId
        // must be cast to int4 — JDBC binds Long as bigint by default.
        String query = "SELECT pg_try_advisory_lock(hashtext('log-import'), CAST(? AS integer))";
        Boolean locked = jdbcTemplate.queryForObject(query, Boolean.class, validationId.intValue());
        if (locked == null || !locked) {
            log.warn("Import already in progress for validation {}", validationId);
            throw new ImportInProgressException(
                    "Import already in progress for validation " + validationId,
                    validationId
            );
        }
        log.debug("Acquired advisory lock for validation {}", validationId);
        return new LockHandle(validationId, jdbcTemplate);
    }

    /**
     * AutoCloseable lock handle. close() swallows unlock failures — the session
     * release on connection return is the real guarantee.
     */
    public static class LockHandle implements AutoCloseable {
        private final Long validationId;
        private final JdbcTemplate jdbcTemplate;
        private boolean released = false;

        LockHandle(Long validationId, JdbcTemplate jdbcTemplate) {
            this.validationId = validationId;
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void close() {
            if (released) {
                return;
            }
            try {
                jdbcTemplate.queryForObject(
                        "SELECT pg_advisory_unlock(hashtext('log-import'), CAST(? AS integer))",
                        Boolean.class,
                        validationId.intValue()
                );
                log.debug("Released advisory lock for validation {}", validationId);
            } catch (DataAccessException e) {
                log.warn("Failed to release advisory lock for validation {} (will be released on session close): {}",
                        validationId, e.getMessage());
            } finally {
                released = true;
            }
        }
    }
}
