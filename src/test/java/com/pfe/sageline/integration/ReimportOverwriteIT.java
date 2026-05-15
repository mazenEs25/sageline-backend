package com.pfe.sageline.integration;

import com.pfe.sageline.dtos.request.LogImportOptionsDTO;
import com.pfe.sageline.dtos.response.LogImportReportDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.service.Import.LogImportService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StreamUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T038 — Re-import on a ticket: the default protects already-measured rows;
 * the overwriteExisting flag opts in.
 */
@SpringBootTest
class ReimportOverwriteIT extends PostgresTestcontainer {

    @Autowired ValidationMeasureTestSeed seed;
    @Autowired LogImportService logImportService;
    @Autowired ValidationMeasureRepository measureRepository;

    private byte[] bwcFixture;

    @BeforeEach
    void setUp() throws Exception {
        try (var stream = new ClassPathResource("fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log").getInputStream()) {
            bwcFixture = StreamUtils.copyToByteArray(stream);
        }
    }

    private MockMultipartFile freshFile() {
        return new MockMultipartFile("file", "bwc.log", "text/plain", bwcFixture);
    }

    @Test
    void reimport_without_flag_protects_manually_overridden_value() throws Exception {
        ProductionLine line = seed.seedLine("OW");
        ValidationZone zone = seed.seedZone("ZONE_OW_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);

        // First import: brings the measures in.
        logImportService.importLog(ticket.getId(), freshFile(), new LogImportOptionsDTO(false), null);

        // Manually override one value to mark it as "user-curated".
        List<ValidationMeasure> rows = measureRepository.findByValidationIdAndMeasureCodeIn(
                ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500"));
        assertThat(rows).hasSize(1);
        ValidationMeasure manual = rows.get(0);
        Double manualValue = 999.99;
        manual.setMeasuredValue(manualValue);
        // Keep status consistent with the ck_vm_deviation_consistency CHECK.
        manual.setStatus(com.pfe.sageline.enums.MeasureStatus.OUT_OF_RANGE);
        manual.setDeviationPct(9999.0);
        measureRepository.save(manual);

        // Second import WITHOUT overwriteExisting: the manually-edited row must be protected,
        // and the would-overwrite section must surface it.
        LogImportReportDTO report = logImportService.importLog(
                ticket.getId(), freshFile(), new LogImportOptionsDTO(false), null);

        assertThat(report.getWouldOverwrite())
                .extracting("measureCode")
                .contains("POWER_RMS_AVG_VSA1_ANT1_5500");

        ValidationMeasure stillManual = measureRepository.findByValidationIdAndMeasureCodeIn(
                ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500")).get(0);
        assertThat(stillManual.getMeasuredValue()).isEqualTo(manualValue);
    }

    @Test
    void reimport_with_flag_replaces_already_measured_rows() throws Exception {
        ProductionLine line = seed.seedLine("OW2");
        ValidationZone zone = seed.seedZone("ZONE_OW2_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);

        // First import populates the row.
        logImportService.importLog(ticket.getId(), freshFile(), new LogImportOptionsDTO(false), null);

        ValidationMeasure before = measureRepository.findByValidationIdAndMeasureCodeIn(
                ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500")).get(0);
        Double firstValue = before.getMeasuredValue();
        before.setMeasuredValue(123.0);
        before.setStatus(com.pfe.sageline.enums.MeasureStatus.OUT_OF_RANGE);
        before.setDeviationPct(500.0);
        measureRepository.save(before);

        // Second import WITH overwriteExisting=true: the row is overwritten with the fixture value.
        logImportService.importLog(ticket.getId(), freshFile(), new LogImportOptionsDTO(true), null);

        ValidationMeasure after = measureRepository.findByValidationIdAndMeasureCodeIn(
                ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500")).get(0);
        assertThat(after.getMeasuredValue()).isEqualTo(firstValue);
    }
}
