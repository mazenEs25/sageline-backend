package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.response.MissingMeasureDTO;
import com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.TransitionBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketTransitionGuardTest {

    private WorkflowReadinessService readinessService;
    private TicketTransitionGuard guard;

    @BeforeEach
    void setUp() {
        readinessService = mock(WorkflowReadinessService.class);
        guard = new TicketTransitionGuard(readinessService);
    }

    @Test
    void check_does_not_throw_when_canTransition_is_true() {
        WorkflowReadinessDTO readiness = new WorkflowReadinessDTO(
            1L, "EN_COURS", "EN_REVUE", 16, 16, 0,
            List.of(), List.of(), true, List.of()
        );
        when(readinessService.computeReadiness(1L, TicketStatus.EN_REVUE)).thenReturn(readiness);

        assertThatNoException().isThrownBy(() -> guard.check(1L, TicketStatus.EN_REVUE));
    }

    @Test
    void check_throws_TransitionBlockedException_when_canTransition_is_false() {
        WorkflowReadinessDTO readiness = new WorkflowReadinessDTO(
            1L, "EN_COURS", "EN_REVUE", 16, 14, 2,
            List.of(new MissingMeasureDTO("M1", "Measure 1", true)),
            List.of(), false, List.of("X", "Y")
        );
        when(readinessService.computeReadiness(1L, TicketStatus.EN_REVUE)).thenReturn(readiness);

        assertThatThrownBy(() -> guard.check(1L, TicketStatus.EN_REVUE))
            .isInstanceOf(TransitionBlockedException.class)
            .satisfies(ex -> {
                TransitionBlockedException tbe = (TransitionBlockedException) ex;
                assertThat(tbe.getReadiness()).isSameAs(readiness);
                assertThat(tbe.getMessage()).contains("X").contains("Y");
            });
    }
}
