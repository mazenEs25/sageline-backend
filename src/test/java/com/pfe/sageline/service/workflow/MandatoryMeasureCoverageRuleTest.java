package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.internal.MandatoryCoverageRow;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MandatoryMeasureCoverageRuleTest {

    private ValidationMeasureRepository repository;
    private MandatoryMeasureCoverageRule rule;
    private Validation ticket;

    @BeforeEach
    void setUp() {
        repository = mock(ValidationMeasureRepository.class);
        rule = new MandatoryMeasureCoverageRule(repository);
        ticket = new Validation();
        ticket.setId(1L);
        ticket.setStatus(TicketStatus.EN_COURS);
    }

    @Test
    void allow_when_targetStatus_is_not_EN_REVUE() {
        RuleVerdict verdict = rule.evaluate(ticket, TicketStatus.EN_COURS);
        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void allow_when_all_mandatory_measures_are_filled() {
        when(repository.coverageSummary(1L)).thenReturn(List.of(
            new MandatoryCoverageRow(true, MeasureStatus.OK, 16)
        ));
        RuleVerdict verdict = rule.evaluate(ticket, TicketStatus.EN_REVUE);
        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void allow_when_no_mandatory_measures_exist() {
        when(repository.coverageSummary(1L)).thenReturn(List.of());
        RuleVerdict verdict = rule.evaluate(ticket, TicketStatus.EN_REVUE);
        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void block_when_mandatory_measures_still_NOT_EXECUTED() {
        when(repository.coverageSummary(1L)).thenReturn(List.of(
            new MandatoryCoverageRow(true, MeasureStatus.NOT_EXECUTED, 2),
            new MandatoryCoverageRow(true, MeasureStatus.OK, 14)
        ));
        RuleVerdict verdict = rule.evaluate(ticket, TicketStatus.EN_REVUE);
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.blockingReasons()).anyMatch(r -> r.contains("2 mandatory measures still in NOT_EXECUTED state"));
    }

    @Test
    void allow_when_only_non_mandatory_measures_are_NOT_EXECUTED() {
        when(repository.coverageSummary(1L)).thenReturn(List.of(
            new MandatoryCoverageRow(false, MeasureStatus.NOT_EXECUTED, 5),
            new MandatoryCoverageRow(true, MeasureStatus.OK, 16)
        ));
        RuleVerdict verdict = rule.evaluate(ticket, TicketStatus.EN_REVUE);
        assertThat(verdict.allowed()).isTrue();
    }
}
