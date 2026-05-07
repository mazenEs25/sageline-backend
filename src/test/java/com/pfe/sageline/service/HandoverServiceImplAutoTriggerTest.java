package com.pfe.sageline.service;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.mappers.HandoverMapper;
import com.pfe.sageline.repository.*;
import com.pfe.sageline.service.impl.HandoverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandoverServiceImplAutoTriggerTest {

    @Mock HandoverRepository handoverRepository;
    @Mock ValidationRepository validationRepository;
    @Mock ValidationAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SecurityUtils securityUtils;
    @Mock HandoverMapper handoverMapper;

    @InjectMocks HandoverServiceImpl service;

    private Validation ticket;
    private User tech;
    private ValidationAssignment assignment;

    @BeforeEach
    void setUp() {
        tech = new User();
        tech.setId(1L);
        tech.setUsername("jean");
        tech.setRole(Role.TECH_VAL);

        ticket = new Validation();
        ticket.setId(10L);
        ticket.setTicketCode("VAL-2026-0010");
        ticket.setStatus(TicketStatus.EN_COURS);

        assignment = new ValidationAssignment();
        assignment.setId(100L);
        assignment.setUser(tech);
        assignment.setValidation(ticket);
        assignment.setStatus(AssignmentStatus.EN_COURS);
        assignment.setAssignmentRole(AssignmentRole.TECH_VALIDATION);
    }

    @Test
    void triggerAutoHandover_happyPath_createsHandoverAndPausesAssignment() {
        when(handoverRepository.findActiveByValidation(10L)).thenReturn(Optional.empty());
        when(assignmentRepository.findByValidationId(10L)).thenReturn(List.of(assignment));
        when(handoverRepository.save(any())).thenAnswer(inv -> {
            TicketHandover h = inv.getArgument(0);
            h.setId(99L);
            return h;
        });
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.triggerAutoHandover(ticket);

        ArgumentCaptor<TicketHandover> handoverCaptor = ArgumentCaptor.forClass(TicketHandover.class);
        verify(handoverRepository).save(handoverCaptor.capture());
        TicketHandover saved = handoverCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.PENDING);
        assertThat(saved.getTriggeredBy()).isEqualTo(TriggerType.SHIFT_END_AUTO);
        assertThat(saved.getFromTech()).isEqualTo(tech);
        assertThat(saved.getScheduledAt()).isNotNull();

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PAUSED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EN_ATTENTE_HANDOVER);
    }

    @Test
    void triggerAutoHandover_alreadyPending_isIdempotentNoOp() {
        TicketHandover existing = new TicketHandover();
        existing.setId(50L);
        existing.setStatus(HandoverStatus.PENDING);
        when(handoverRepository.findActiveByValidation(10L)).thenReturn(Optional.of(existing));

        service.triggerAutoHandover(ticket);

        verify(handoverRepository, never()).save(any());
        verify(assignmentRepository, never()).save(any());
        verify(validationRepository, never()).save(any());
    }

    @Test
    void triggerAutoHandover_noActiveAssignment_logsWarnAndDoesNothing() {
        when(handoverRepository.findActiveByValidation(10L)).thenReturn(Optional.empty());
        when(assignmentRepository.findByValidationId(10L)).thenReturn(List.of());

        service.triggerAutoHandover(ticket);

        verify(handoverRepository, never()).save(any());
        verify(validationRepository, never()).save(any());
    }
}
