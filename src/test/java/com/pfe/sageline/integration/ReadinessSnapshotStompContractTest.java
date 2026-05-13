package com.pfe.sageline.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.service.ValidationMeasureService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadinessSnapshotStompContractTest extends PostgresTestcontainer {

    @Value("${local.server.port}")
    private int port;

    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ValidationMeasureService measureService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("stomp-test-user");
    }

    private WebSocketStompClient buildStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession connect(WebSocketStompClient client) throws Exception {
        return client.connectAsync(
            "ws://localhost:" + port + "/ws",
            new WebSocketHttpHeaders(),
            new StompHeaders(),
            new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<WorkflowReadinessDTO> subscribeReadiness(StompSession session, long ticketId) {
        BlockingQueue<WorkflowReadinessDTO> queue = new LinkedBlockingQueue<>();
        session.subscribe("/topic/validation." + ticketId + ".readiness",
            new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) {
                    return WorkflowReadinessDTO.class;
                }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((WorkflowReadinessDTO) payload);
                }
            });
        return queue;
    }

    private long seedTicketWithMandatoryMeasures(String prefix, int mandatoryCount) {
        ProductionLine line = seed.seedLine(prefix);
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_" + prefix + "_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        for (int i = 1; i <= mandatoryCount; i++) {
            seed.seedCatalogRow(pt, prefix + "_M" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        }
        measureService.instantiateFromCatalog(ticket.getId());
        return ticket.getId();
    }

    @Test
    void single_measure_update_fires_one_snapshot_and_mandatoryFilled_increments() throws Exception {
        long ticketId = seedTicketWithMandatoryMeasures("SU", 4);
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        assertThat(measures).hasSize(4);

        WebSocketStompClient client = buildStompClient();
        StompSession session = connect(client);
        BlockingQueue<WorkflowReadinessDTO> queue = subscribeReadiness(session, ticketId);

        // Update the first measure (NOT_EXECUTED → OK)
        ValidationMeasure first = measures.get(0);
        UpdateMeasureRequest req = new UpdateMeasureRequest();
        req.setMeasuredValue(12.0);
        measureService.update(ticketId, first.getId(), req);

        WorkflowReadinessDTO snapshot = queue.poll(5, TimeUnit.SECONDS);
        assertThat(snapshot).as("Expected a STOMP snapshot within 5s").isNotNull();
        assertThat(snapshot.ticketId()).isEqualTo(ticketId);
        assertThat(snapshot.mandatoryTotal()).isEqualTo(4);
        assertThat(snapshot.mandatoryFilled()).isEqualTo(1);
        assertThat(snapshot.mandatoryMissing()).isEqualTo(3);

        // Only one message should have arrived for one update
        WorkflowReadinessDTO extra = queue.poll(1, TimeUnit.SECONDS);
        assertThat(extra).as("Only one snapshot should be published per update").isNull();

        session.disconnect();
    }

    @Test
    void delete_fires_snapshot_and_mandatoryFilled_decrements() throws Exception {
        long ticketId = seedTicketWithMandatoryMeasures("DEL", 2);
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        assertThat(measures).hasSize(2);

        // Pre-fill both measures so mandatoryFilled = 2
        measures.forEach(m -> {
            UpdateMeasureRequest req = new UpdateMeasureRequest();
            req.setMeasuredValue(12.0);
            measureService.update(ticketId, m.getId(), req);
        });

        WebSocketStompClient client = buildStompClient();
        StompSession session = connect(client);
        BlockingQueue<WorkflowReadinessDTO> queue = subscribeReadiness(session, ticketId);

        // Delete one measure → mandatoryFilled should drop to 1
        measureService.delete(ticketId, measures.get(0).getId());

        WorkflowReadinessDTO snapshot = queue.poll(5, TimeUnit.SECONDS);
        assertThat(snapshot).as("Expected a STOMP snapshot after delete").isNotNull();
        assertThat(snapshot.ticketId()).isEqualTo(ticketId);
        assertThat(snapshot.mandatoryTotal()).isEqualTo(1);
        assertThat(snapshot.mandatoryFilled()).isEqualTo(1);
        assertThat(snapshot.mandatoryMissing()).isEqualTo(0);

        session.disconnect();
    }

    @Test
    void batchCreate_fires_one_coalesced_snapshot() throws Exception {
        ProductionLine line = seed.seedLine("BC");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_BC_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        long ticketId = ticket.getId();

        WebSocketStompClient client = buildStompClient();
        StompSession session = connect(client);
        BlockingQueue<WorkflowReadinessDTO> queue = subscribeReadiness(session, ticketId);

        // Build 3 ad-hoc measures via batchCreate (one publish per batch call)
        List<CreateMeasureRequest> items = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            CreateMeasureRequest item = new CreateMeasureRequest();
            item.setMeasureCode("BC_ADHOC_" + i + "_" + System.nanoTime());
            item.setMeasureLabel("Ad-hoc " + i);
            item.setCategory(MeasureCategory.OTHER);
            item.setUnit("dBm");
            item.setLowerBound(10.0);
            item.setUpperBound(15.0);
            item.setMeasuredValue(12.0);
            items.add(item);
        }
        BatchCreateMeasureRequest batchReq = new BatchCreateMeasureRequest();
        batchReq.setMeasures(items);
        measureService.batchCreate(ticketId, batchReq);

        WorkflowReadinessDTO snapshot = queue.poll(5, TimeUnit.SECONDS);
        assertThat(snapshot).as("Expected exactly one coalesced STOMP snapshot after batchCreate").isNotNull();
        assertThat(snapshot.ticketId()).isEqualTo(ticketId);

        // Must not receive another snapshot for the same batch
        WorkflowReadinessDTO extra = queue.poll(2, TimeUnit.SECONDS);
        assertThat(extra).as("batchCreate should publish only one coalesced snapshot").isNull();

        session.disconnect();
    }

    @Test
    void cross_ticket_isolation() throws Exception {
        long ticketA = seedTicketWithMandatoryMeasures("XT_A", 2);
        long ticketB = seedTicketWithMandatoryMeasures("XT_B", 2);

        WebSocketStompClient client = buildStompClient();
        StompSession session = connect(client);
        BlockingQueue<WorkflowReadinessDTO> queueA = subscribeReadiness(session, ticketA);

        // Mutate ticket B — A's queue should receive nothing
        List<ValidationMeasure> measuresB = measureRepository.findAllByValidationIdFetchTemplate(ticketB);
        assertThat(measuresB).isNotEmpty();
        UpdateMeasureRequest req = new UpdateMeasureRequest();
        req.setMeasuredValue(12.0);
        measureService.update(ticketB, measuresB.get(0).getId(), req);

        WorkflowReadinessDTO isolation = queueA.poll(2, TimeUnit.SECONDS);
        assertThat(isolation).as("Cross-ticket isolation violated: ticket A received a snapshot from ticket B mutation").isNull();

        session.disconnect();
    }
}
