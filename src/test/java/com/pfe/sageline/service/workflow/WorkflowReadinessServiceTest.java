package com.pfe.sageline.service.workflow;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.TransitionBlockedException;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.service.ValidationMeasureService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkflowReadinessServiceTest extends PostgresTestcontainer {

    @Autowired WorkflowReadinessService readinessService;
    @Autowired TicketTransitionGuard transitionGuard;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ValidationMeasureService measureService;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test");
    }

    private long seedFullTicket() {
        ProductionLine line = seed.seedLine("WRS");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_WRS_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        for (int i = 1; i <= 16; i++) {
            seed.seedCatalogRow(pt, "WRS_M" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        }
        measureService.instantiateFromCatalog(ticket.getId());
        return ticket.getId();
    }

    @Test
    void idempotent_reads() {
        long ticketId = seedFullTicket();

        WorkflowReadinessDTO first = readinessService.computeReadiness(ticketId, TicketStatus.EN_REVUE);
        for (int i = 0; i < 4; i++) {
            WorkflowReadinessDTO repeat = readinessService.computeReadiness(ticketId, TicketStatus.EN_REVUE);
            assertThat(repeat).isEqualTo(first);
        }
    }

    @Test
    void probe_equals_refusal_readiness() {
        long ticketId = seedFullTicket();
        // Leave 2 measures as NOT_EXECUTED, fill 14
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        measures.stream().limit(14).forEach(m -> {
            m.setMeasuredValue(12.0);
            m.setStatus(MeasureStatus.OK);
            m.setDeviationPct(40.0);
            measureRepository.save(m);
        });

        WorkflowReadinessDTO probe = readinessService.computeReadiness(ticketId, TicketStatus.EN_REVUE);
        assertThat(probe.canTransition()).isFalse();

        assertThatThrownBy(() -> transitionGuard.check(ticketId, TicketStatus.EN_REVUE))
            .isInstanceOf(TransitionBlockedException.class)
            .satisfies(ex -> {
                WorkflowReadinessDTO refusal = ((TransitionBlockedException) ex).getReadiness();
                assertThat(refusal).isEqualTo(probe);
            });
    }

    @Test
    void latency_smoke() {
        long ticketId = seedFullTicket();
        // Fill all measures for realistic load
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        measures.forEach(m -> {
            m.setMeasuredValue(12.0);
            m.setStatus(MeasureStatus.OK);
            m.setDeviationPct(40.0);
            measureRepository.save(m);
        });

        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long start = System.nanoTime();
            readinessService.computeReadiness(ticketId, TicketStatus.EN_REVUE);
            latencies.add(System.nanoTime() - start);
        }
        latencies.sort(Long::compareTo);
        long medianNs = latencies.get(latencies.size() / 2);
        assertThat(medianNs).as("Median readiness latency should be under 300ms").isLessThan(300_000_000L);
    }

    @Test
    void non_en_cours_ticket_returns_can_transition_false() {
        ProductionLine line = seed.seedLine("NENC");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_NENC_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_REVUE, line, zone);

        WorkflowReadinessDTO dto = readinessService.computeReadiness(ticket.getId(), TicketStatus.EN_REVUE);

        assertThat(dto.canTransition()).isFalse();
        assertThat(dto.currentStatus()).isEqualTo("EN_REVUE");
        assertThat(dto.blockingReasons()).anyMatch(r -> r.contains("is not eligible for transition to EN_REVUE"));
    }
}
