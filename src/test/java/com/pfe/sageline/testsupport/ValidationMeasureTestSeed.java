package com.pfe.sageline.testsupport;

import com.pfe.sageline.entity.Phase;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Secteur;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.Priority;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.PhaseRepository;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.repository.ProductionLineRepository;
import com.pfe.sageline.repository.SecteurRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.repository.ValidationZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ValidationMeasureTestSeed {

    private static final AtomicLong SEED_COUNTER = new AtomicLong(0);

    private final ValidationRepository validationRepository;
    private final PosteMeasureCatalogRepository catalogRepository;
    private final ProductionLineRepository productionLineRepository;
    private final ValidationZoneRepository validationZoneRepository;
    private final SecteurRepository secteurRepository;
    private final PhaseRepository phaseRepository;

    public Validation seedTicketInStatus(TicketStatus status, ProductionLine line, ValidationZone zone) {
        // ticket_code is varchar(20); max "VT" + 12 digits = 14 chars
        String code = "VT" + String.format("%012d", SEED_COUNTER.incrementAndGet());
        Validation ticket = Validation.builder()
                .ticketCode(code)
                .status(status)
                .productionLine(line)
                .validationZone(zone)
                .priority(Priority.NORMALE)
                .build();
        return validationRepository.save(ticket);
    }

    public PosteMeasureCatalog seedCatalogRow(PosteType posteType, String measureCode,
                                               double lower, double upper,
                                               String unit, boolean mandatory) {
        PosteMeasureCatalog row = PosteMeasureCatalog.builder()
                .posteType(posteType)
                .measureCode(measureCode)
                .measureLabel(measureCode)
                .category(MeasureCategory.OTHER)
                .defaultUnit(unit)
                .defaultLowerBound(lower)
                .defaultUpperBound(upper)
                .mandatory(mandatory)
                .displayOrder(1)
                .active(true)
                .build();
        return catalogRepository.save(row);
    }

    public ProductionLine seedLine(String code) {
        long seq = SEED_COUNTER.incrementAndGet();
        String prefix = code.length() > 3 ? code.substring(0, 3) : code;
        String seq6 = String.format("%06d", seq);   // 6 digits, never repeats within JVM

        Secteur secteur = secteurRepository.save(Secteur.builder()
                .code("S" + prefix + seq6)   // max 1+3+6 = 10 chars
                .name("Secteur " + code)
                .active(true)
                .build());

        Phase phase = phaseRepository.save(Phase.builder()
                .code("P" + prefix + seq6)   // max 1+3+6 = 10 chars, fits in 20
                .name("Phase " + code)
                .secteur(secteur)
                .orderIndex(1)
                .active(true)
                .build());

        ProductionLine line = new ProductionLine();
        line.setCode(code + "_" + System.nanoTime());
        line.setName("Test Line " + code);
        line.setActive(true);
        line.setPhase(phase);
        line.setLineNumber(1);
        return productionLineRepository.save(line);
    }

    public ValidationZone seedZone(String name, PosteType posteType, ProductionLine line) {
        ValidationZone zone = ValidationZone.builder()
                .name(name)
                .posteType(posteType)
                .productionLine(line)
                .orderInLine(1)
                .requiresToolCheck(false)
                .build();
        return validationZoneRepository.save(zone);
    }
}
