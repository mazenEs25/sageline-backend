package com.pfe.sageline.scheduler;

import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.service.HandoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShiftEndHandoverJob {

    private final ValidationRepository validationRepository;
    private final HandoverService handoverService;

    /**
     * Every weekday at 16:45, sweep all in-progress tickets and initiate handovers
     * for those that have no existing pending/accepted handover (idempotent).
     */
    @Scheduled(cron = "0 45 16 * * MON-FRI")
    //@Scheduled(cron = "0 */1 * * * *")
    public void triggerShiftEndHandovers() {
        log.info("[ShiftEndHandoverJob] Starting shift-end handover sweep");

        List<Validation> eligible =
                validationRepository.findEligibleForShiftEndHandover(TicketStatus.EN_COURS);

        log.info("[ShiftEndHandoverJob] Found {} eligible ticket(s) for handover", eligible.size());

        int success = 0;
        int skipped = 0;
        for (Validation ticket : eligible) {
            try {
                handoverService.triggerAutoHandover(ticket);
                success++;
            } catch (Exception e) {
                log.warn("[ShiftEndHandoverJob] Handover échoué pour ticket {}: {}",
                        ticket.getTicketCode(), e.getMessage());
                skipped++;
            }
        }

        log.info("[ShiftEndHandoverJob] Sweep terminé: {} passation(s) créée(s), {} ignorée(s)",
                success, skipped);
    }
}
