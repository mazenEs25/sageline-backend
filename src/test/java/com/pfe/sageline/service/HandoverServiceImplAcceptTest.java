package com.pfe.sageline.service;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.HandoverMapper;
import com.pfe.sageline.repository.*;
import com.pfe.sageline.service.impl.HandoverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandoverServiceImplAcceptTest {

    @Mock HandoverRepository handoverRepository;
    @Mock ValidationRepository validationRepository;
    @Mock ValidationAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SecurityUtils securityUtils;
    @Mock HandoverMapper handoverMapper;

    @InjectMocks HandoverServiceImpl service;

    private ProductionLine line;
    private Validation validation;
    private User fromTech;
    private User acceptingTech;
    private ValidationZone zone;
    private TicketHandover handover;
    private ValidationAssignment pausedAssignment;

    @BeforeEach
    void setUp() {
        line = new ProductionLine();
        line.setId(5L);
        line.setCode("L5");
        line.setName("Ligne 5");

        zone = new ValidationZone();
        zone.setId(20L);
        zone.setProductionLine(line);

        fromTech = new User();
        fromTech.setId(1L);
        fromTech.setUsername("jean");
        fromTech.setRole(Role.TECH_VAL);

        acceptingTech = new User();
        acceptingTech.setId(2L);
        acceptingTech.setUsername("ahmed");
        acceptingTech.setRole(Role.TECH_VAL);

        validation = new Validation();
        validation.setId(10L);
        validation.setTicketCode("VAL-2026-0010");
        validation.setStatus(TicketStatus.EN_ATTENTE_HANDOVER);
        validation.setProductionLine(line);

        handover = new TicketHandover();
        handover.setId(99L);
        handover.setValidation(validation);
        handover.setFromTech(fromTech);
        handover.setStatus(HandoverStatus.PENDING);
        handover.setTriggeredBy(TriggerType.SHIFT_END_AUTO);
        handover.setScheduledAt(LocalDateTime.now().minusHours(1));

        pausedAssignment = new ValidationAssignment();
        pausedAssignment.setId(100L);
        pausedAssignment.setUser(fromTech);
        pausedAssignment.setValidation(validation);
        pausedAssignment.setZone(zone);
        pausedAssignment.setStatus(AssignmentStatus.PAUSED);
        pausedAssignment.setAssignmentRole(AssignmentRole.TECH_VALIDATION);
    }

    @Test
    void acceptHandover_happyPath_completesHandoverAndResumesTicket() {
        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));

        // Zone-locality: accepting tech is assigned to the same production line
        ValidationAssignment techAssignment = new ValidationAssignment();
        techAssignment.setUser(acceptingTech);
        techAssignment.setZone(zone);
        when(assignmentRepository.findByUserId(2L)).thenReturn(List.of(techAssignment));

        // No active assignment on the ticket
        when(assignmentRepository.findByValidationId(10L)).thenReturn(List.of(pausedAssignment));

        // CAS succeeds — all args must use matchers when any() is present
        when(handoverRepository.compareAndSetStatus(eq(99L), eq(HandoverStatus.PENDING),
                eq(HandoverStatus.COMPLETED), eq(acceptingTech), any())).thenReturn(1);

        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(handoverMapper.toResponse(any())).thenReturn(null);

        service.acceptHandover(99L);

        verify(handoverRepository).compareAndSetStatus(eq(99L), eq(HandoverStatus.PENDING),
                eq(HandoverStatus.COMPLETED), eq(acceptingTech), any());
        assertThat(validation.getStatus()).isEqualTo(TicketStatus.EN_COURS);
        assertThat(handover.getStatus()).isEqualTo(HandoverStatus.COMPLETED);
        assertThat(handover.getToTech()).isEqualTo(acceptingTech);
    }

    @Test
    void acceptHandover_crossZone_throwsValidationException() {
        // A different production line for the accepting tech's assignments
        ProductionLine otherLine = new ProductionLine();
        otherLine.setId(99L);
        ValidationZone otherZone = new ValidationZone();
        otherZone.setProductionLine(otherLine);
        ValidationAssignment foreignAssignment = new ValidationAssignment();
        foreignAssignment.setZone(otherZone);
        foreignAssignment.setUser(acceptingTech);

        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));
        when(assignmentRepository.findByUserId(2L)).thenReturn(List.of(foreignAssignment));

        assertThatThrownBy(() -> service.acceptHandover(99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cross-zone self-accept not allowed");

        verify(handoverRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }

    @Test
    void acceptHandover_legacyTicketNoProductionLine_zoneLocalityEnforcedViaValidationZone() {
        // Ticket was created before the productionLine FK migration — productionLine is null,
        // zone-locality must still be enforced via validationZone.productionLine.
        validation.setProductionLine(null);
        validation.setValidationZone(zone);  // zone.productionLine = line (id=5)

        // Accepting tech is on a different line — should be rejected
        ProductionLine otherLine = new ProductionLine();
        otherLine.setId(77L);
        ValidationZone otherZone = new ValidationZone();
        otherZone.setProductionLine(otherLine);
        ValidationAssignment foreignAssignment = new ValidationAssignment();
        foreignAssignment.setZone(otherZone);
        foreignAssignment.setUser(acceptingTech);

        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));
        when(assignmentRepository.findByUserId(2L)).thenReturn(List.of(foreignAssignment));

        assertThatThrownBy(() -> service.acceptHandover(99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cross-zone self-accept not allowed");

        verify(handoverRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }

    @Test
    void acceptHandover_legacyTicketBothFieldsNull_throwsValidationException() {
        // Neither productionLine nor validationZone is set — guard must not silently pass.
        validation.setProductionLine(null);
        validation.setValidationZone(null);

        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));

        assertThatThrownBy(() -> service.acceptHandover(99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cross-zone self-accept not allowed");

        verify(handoverRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }

    @Test
    void acceptHandover_casReturnsZero_throwsAlreadyAccepted() {
        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));

        ValidationAssignment techAssignment = new ValidationAssignment();
        techAssignment.setUser(acceptingTech);
        techAssignment.setZone(zone);
        when(assignmentRepository.findByUserId(2L)).thenReturn(List.of(techAssignment));
        when(assignmentRepository.findByValidationId(10L)).thenReturn(List.of(pausedAssignment));

        // CAS loses the race
        when(handoverRepository.compareAndSetStatus(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.acceptHandover(99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already accepted");
    }

    @Test
    void acceptHandover_alreadyActiveAssignment_throwsValidationException() {
        when(handoverRepository.findById(99L)).thenReturn(Optional.of(handover));
        when(securityUtils.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(acceptingTech));

        ValidationAssignment techAssignment = new ValidationAssignment();
        techAssignment.setUser(acceptingTech);
        techAssignment.setZone(zone);
        when(assignmentRepository.findByUserId(2L)).thenReturn(List.of(techAssignment));

        // Another tech is already EN_COURS
        ValidationAssignment activeOther = new ValidationAssignment();
        activeOther.setUser(fromTech);
        activeOther.setStatus(AssignmentStatus.EN_COURS);
        when(assignmentRepository.findByValidationId(10L)).thenReturn(List.of(activeOther));

        assertThatThrownBy(() -> service.acceptHandover(99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("technicien actif");

        verify(handoverRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }
}
