package com.pfe.sageline.service.Import;

import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.UnmatchedReason;
import com.pfe.sageline.repository.MeasureCodeAliasRepository;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.service.Import.parser.ParsedMeasure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class MeasureMatcher {

    private final PosteMeasureCatalogRepository catalogRepository;
    private final MeasureCodeAliasRepository aliasRepository;

    public MeasureMatcher(PosteMeasureCatalogRepository catalogRepository,
                          MeasureCodeAliasRepository aliasRepository) {
        this.catalogRepository = catalogRepository;
        this.aliasRepository = aliasRepository;
    }

    public MatchResult match(PosteType posteType, List<ParsedMeasure> measures) {
        List<MatchedEntry> matched = new ArrayList<>();
        List<UnmatchedEntry> unmatched = new ArrayList<>();

        for (ParsedMeasure parsedMeasure : measures) {
            String code = parsedMeasure.code();

            Optional<PosteMeasureCatalog> catalogMatch = catalogRepository
                    .findByPosteTypeAndMeasureCodeAndActiveTrue(posteType, code);

            if (catalogMatch.isPresent()) {
                matched.add(new MatchedEntry(parsedMeasure, code, catalogMatch.get()));
                log.debug("Catalog match for code: {}", code);
                continue;
            }

            Optional<String> aliasedCode = aliasRepository
                    .findByPosteTypeAndSourceCodeAndActiveTrue(posteType, code)
                    .map(alias -> alias.getCatalogMeasureCode());

            if (aliasedCode.isPresent()) {
                String resolvedCode = aliasedCode.get();
                Optional<PosteMeasureCatalog> aliasedMatch = catalogRepository
                        .findByPosteTypeAndMeasureCodeAndActiveTrue(posteType, resolvedCode);
                if (aliasedMatch.isPresent()) {
                    matched.add(new MatchedEntry(parsedMeasure, resolvedCode, aliasedMatch.get()));
                    log.debug("Alias match for code: {} -> {}", code, resolvedCode);
                } else {
                    unmatched.add(new UnmatchedEntry(code, parsedMeasure.measuredValue(),
                            parsedMeasure.unit(), UnmatchedReason.NO_TEMPLATE_FOR_POSTE_TYPE,
                            "Alias resolved to '" + resolvedCode + "' but no template found in catalog for poste type " + posteType));
                }
            } else {
                unmatched.add(new UnmatchedEntry(code, parsedMeasure.measuredValue(),
                        parsedMeasure.unit(), UnmatchedReason.NO_TEMPLATE_FOR_POSTE_TYPE,
                        "No template found for measure code '" + code + "' in catalog of poste type " + posteType));
            }
        }

        return new MatchResult(matched, unmatched);
    }

    /**
     * A matched entry carries the parsed measure, the resolved catalog code,
     * and the full catalog template so downstream code can fall back to
     * template bounds / label / category when the log omits them.
     */
    public record MatchedEntry(ParsedMeasure parsedMeasure, String resolvedCode, PosteMeasureCatalog template) {
        public Long templateId() { return template.getId(); }
    }

    public record UnmatchedEntry(String code, Double measuredValue, String unit,
                                 UnmatchedReason reason, String reasonDetail) {}

    public record MatchResult(List<MatchedEntry> matched, List<UnmatchedEntry> unmatched) {}
}
