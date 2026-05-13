package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.internal.MandatoryCoverageRow;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandatoryMeasureCoverageRule implements TransitionRule {

    private final ValidationMeasureRepository repository;

    @Override
    public RuleVerdict evaluate(Validation ticket, TicketStatus targetStatus) {
        if (targetStatus != TicketStatus.EN_REVUE) return RuleVerdict.allow();
        long missing = repository.coverageSummary(ticket.getId()).stream()
                .filter(r -> r.mandatory() && r.status() == MeasureStatus.NOT_EXECUTED)
                .mapToLong(MandatoryCoverageRow::count)
                .sum();
        return missing == 0
            ? RuleVerdict.allow()
            : RuleVerdict.block(missing + " mandatory measures still in NOT_EXECUTED state");
    }
}
