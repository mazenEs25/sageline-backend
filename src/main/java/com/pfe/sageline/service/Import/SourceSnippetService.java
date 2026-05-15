package com.pfe.sageline.service.Import;

import com.pfe.sageline.Config.LogImportProperties;
import com.pfe.sageline.dtos.response.SourceSnippetDTO;
import com.pfe.sageline.entity.MeasureCodeAlias;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.repository.MeasureCodeAliasRepository;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SourceSnippetService {

    private final ValidationMeasureRepository measureRepository;
    private final MeasureCodeAliasRepository aliasRepository;
    private final LogStorageService storage;
    private final LogImportProperties properties;

    public SourceSnippetService(
            ValidationMeasureRepository measureRepository,
            MeasureCodeAliasRepository aliasRepository,
            LogStorageService storage,
            LogImportProperties properties) {
        this.measureRepository = measureRepository;
        this.aliasRepository = aliasRepository;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SourceSnippetDTO snippet(Long validationId, Long measureId) {
        ValidationMeasure measure = measureRepository.findByIdAndValidationId(measureId, validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Measure not found"));

        if (measure.getImportedLogFile() == null) {
            throw new ResourceNotFoundException("Measure has no source log; entered manually");
        }

        // The measure.measureCode is the catalog-resolved code (e.g. POWER_RMS_AVG_VSA1_ANT1_5500).
        // The fixture log may contain a source code that was aliased to it
        // (e.g. POWER_RMS_AVG_VSA1). Try the resolved code first, then each known
        // aliased source code, until a snippet is found.
        List<String> searchTerms = buildSearchTerms(measure);
        Path logPath = Paths.get(measure.getImportedLogFile().getStoredPath());
        LogStorageService.SnippetResult snippetResult = null;
        for (String term : searchTerms) {
            snippetResult = storage.readSnippetAround(logPath, term, properties.getSnippetLines());
            if (snippetResult != null) break;
        }

        if (snippetResult == null) {
            return SourceSnippetDTO.builder()
                    .measureId(measureId)
                    .originalFilename(measure.getSourceLogFile())
                    .detectedFormat(measure.getImportedLogFile().getDetectedFormat())
                    .snippet(null)
                    .available(false)
                    .startLine(null)
                    .endLine(null)
                    .build();
        }

        return SourceSnippetDTO.builder()
                .measureId(measureId)
                .originalFilename(measure.getSourceLogFile())
                .detectedFormat(measure.getImportedLogFile().getDetectedFormat())
                .snippet(snippetResult.text())
                .available(true)
                .startLine(snippetResult.startLine())
                .endLine(snippetResult.endLine())
                .build();
    }

    private List<String> buildSearchTerms(ValidationMeasure measure) {
        List<String> terms = new ArrayList<>();
        terms.add(measure.getMeasureCode());
        if (measure.getCatalogTemplate() != null) {
            var posteType = measure.getCatalogTemplate().getPosteType();
            List<MeasureCodeAlias> aliases = aliasRepository
                    .findByPosteTypeAndCatalogMeasureCodeAndActiveTrue(posteType, measure.getMeasureCode());
            for (MeasureCodeAlias a : aliases) {
                terms.add(a.getSourceCode());
            }
        }
        return terms;
    }
}
