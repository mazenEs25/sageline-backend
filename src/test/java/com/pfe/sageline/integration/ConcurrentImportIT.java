package com.pfe.sageline.integration;

import com.pfe.sageline.dtos.request.LogImportOptionsDTO;
import com.pfe.sageline.dtos.response.LogImportReportDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.ImportInProgressException;
import com.pfe.sageline.repository.ImportedLogFileRepository;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T037 — Two concurrent imports on the same ticket: exactly one succeeds; the
 * other returns ImportInProgressException via the advisory-lock guard.
 */
@SpringBootTest
class ConcurrentImportIT extends PostgresTestcontainer {

    @Autowired ValidationMeasureTestSeed seed;
    @Autowired LogImportService logImportService;
    @Autowired ImportedLogFileRepository importedLogFileRepository;

    private byte[] bwcFixture;

    @BeforeEach
    void setUp() throws Exception {
        try (var stream = new ClassPathResource("fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log").getInputStream()) {
            bwcFixture = StreamUtils.copyToByteArray(stream);
        }
    }

    @Test
    void two_parallel_imports_on_same_ticket_yield_one_success_one_conflict() throws Exception {
        ProductionLine line = seed.seedLine("CI");
        ValidationZone zone = seed.seedZone("ZONE_CI_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        Long tid = ticket.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger lockConflicts = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        Runnable doImport = () -> {
            try {
                start.await();
                MockMultipartFile file = new MockMultipartFile(
                        "file", "bwc.log", "text/plain", bwcFixture);
                LogImportReportDTO report = logImportService.importLog(
                        tid, file, new LogImportOptionsDTO(false), null);
                if (report != null) successes.incrementAndGet();
            } catch (ImportInProgressException e) {
                lockConflicts.incrementAndGet();
            } catch (Throwable t) {
                unexpected.compareAndSet(null, t);
            }
        };

        pool.submit(doImport);
        pool.submit(doImport);
        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        if (unexpected.get() != null) {
            throw new AssertionError("Unexpected error in concurrent import", unexpected.get());
        }
        // Exactly one success + one conflict — covers SC-008.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(lockConflicts.get()).isEqualTo(1);

        // No duplicate ImportedLogFile rows for the ticket.
        assertThat(importedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(tid)).hasSize(1);
    }
}
