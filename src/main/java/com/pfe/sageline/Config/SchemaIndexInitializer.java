package com.pfe.sageline.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Partial unique index: prevents two concurrent PENDING or ACCEPTED rows for the same
     * ticket, closing the TOCTOU race in HandoverService. COMPLETED/CANCELLED rows are
     * excluded so a ticket can accumulate multiple historical handovers.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createPartialIndexes() {
        try {
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_handover_validation_active
                ON ticket_handovers(validation_id)
                WHERE status IN ('PENDING', 'ACCEPTED')
                """);
            log.info("Partial unique index uq_handover_validation_active ensured");
        } catch (Exception e) {
            log.warn("Could not create partial index uq_handover_validation_active: {}", e.getMessage());
        }
    }
}
