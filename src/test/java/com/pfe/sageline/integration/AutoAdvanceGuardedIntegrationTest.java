package com.pfe.sageline.integration;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationPosteStatus;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.Role;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.UserRepository;
import com.pfe.sageline.repository.ValidationPosteStatusRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.service.ValidationMeasureService;
import com.pfe.sageline.service.ValidationService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

/**
 * Verifies the auto-advance path inside markPosteDone():
 *   - when mandatory measures are NOT_EXECUTED the guard blocks and markPosteDone()
 *     swallows the exception (no throw to caller), leaving the ticket in EN_COURS.
 *   - when all mandatory measures are filled the guard passes and the ticket
 *     advances to EN_REVUE automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AutoAdvanceGuardedIntegrationTest extends PostgresTestcontainer {

    @Autowired ValidationService validationService;
    @Autowired ValidationMeasureService measureService;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationRepository validationRepository;
    @Autowired ValidationPosteStatusRepository posteStatusRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private User actor;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("auto-guard-test");
        actor = userRepository.save(User.builder()
                .username("actor_" + System.nanoTime())
                .email("actor_" + System.nanoTime() + "@test.com")
                .role(Role.TECH_VAL)
                .build());
    }

    @Test
    void markPosteDone_swallows_guard_block_and_ticket_stays_EN_COURS_when_mandatory_missing() {
        ProductionLine line = seed.seedLine("AAG");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_AAG_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        long ticketId = ticket.getId();

        // Seed one mandatory measure — stays NOT_EXECUTED, will block auto-advance
        seed.seedCatalogRow(pt, "AAG_M1_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        measureService.instantiateFromCatalog(ticketId);

        // Seed the single poste row (the "last poste" for this ticket)
        ValidationPosteStatus poste = posteStatusRepository.save(ValidationPosteStatus.builder()
                .validation(ticket)
                .zone(zone)
                .status(TicketStatus.EN_COURS)
                .orderInLine(1)
                .build());

        // markPosteDone() for the last poste triggers auto-advance;
        // the guard should block silently — no exception must escape
        assertThatNoException().isThrownBy(() ->
                validationService.markPosteDone(ticketId, zone.getId(), "CONFORME", null, actor.getId()));

        // Guard must not have mutated the ticket status
        Validation after = validationRepository.findById(ticketId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_COURS);
    }

    @Test
    void markPosteDone_auto_advances_to_EN_REVUE_when_no_mandatory_missing() {
        ProductionLine line = seed.seedLine("AAH");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_AAH_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        long ticketId = ticket.getId();

        // No catalog rows → no mandatory measures → coverage rule passes
        measureService.instantiateFromCatalog(ticketId);

        // Seed the single poste row
        posteStatusRepository.save(ValidationPosteStatus.builder()
                .validation(ticket)
                .zone(zone)
                .status(TicketStatus.EN_COURS)
                .orderInLine(1)
                .build());

        // All postes done + no mandatory missing → auto-advance succeeds
        assertThatNoException().isThrownBy(() ->
                validationService.markPosteDone(ticketId, zone.getId(), "CONFORME", null, actor.getId()));

        Validation after = validationRepository.findById(ticketId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_REVUE);
    }
}
