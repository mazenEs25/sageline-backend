package com.pfe.sageline.repository;

import com.pfe.sageline.entity.ValidationMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationMeasureRepository extends JpaRepository<ValidationMeasure, Long> {

    @Query("""
            SELECT vm FROM ValidationMeasure vm
            LEFT JOIN FETCH vm.catalogTemplate
            WHERE vm.validation.id = :validationId
            ORDER BY vm.measureCode, vm.antenna, vm.frequencyMhz
            """)
    List<ValidationMeasure> findAllByValidationIdFetchTemplate(@Param("validationId") Long validationId);

    Optional<ValidationMeasure> findByIdAndValidationId(Long id, Long validationId);

    boolean existsByValidationIdAndMeasureCodeAndAntennaAndFrequencyMhzAndModulationScheme(
            Long validationId, String measureCode, String antenna,
            Integer frequencyMhz, String modulationScheme);

    /**
     * Given a ticket and a candidate set of catalog template ids,
     * returns the subset of those template ids already present on the ticket.
     */
    @Query("""
            SELECT vm.catalogTemplate.id FROM ValidationMeasure vm
            WHERE vm.validation.id = :validationId
              AND vm.catalogTemplate.id IN :templateIds
            """)
    List<Long> findCatalogTemplateIdsPresentOnTicket(@Param("validationId") Long validationId,
                                                     @Param("templateIds") Collection<Long> templateIds);

    @org.springframework.data.jpa.repository.Query("""
           SELECT new com.pfe.sageline.dtos.internal.MandatoryCoverageRow(
                  vm.mandatoryAtCreation,
                  vm.status,
                  COUNT(vm))
           FROM   ValidationMeasure vm
           WHERE  vm.validation.id = :validationId
           GROUP  BY vm.mandatoryAtCreation, vm.status
           """)
    java.util.List<com.pfe.sageline.dtos.internal.MandatoryCoverageRow> coverageSummary(
            @org.springframework.data.repository.query.Param("validationId") Long validationId);

    @org.springframework.data.jpa.repository.Query("""
           SELECT new com.pfe.sageline.dtos.response.MissingMeasureDTO(
                  vm.measureCode, vm.measureLabel, true)
           FROM   ValidationMeasure vm
           WHERE  vm.validation.id      = :validationId
             AND  vm.mandatoryAtCreation = true
             AND  vm.status              = com.pfe.sageline.enums.MeasureStatus.NOT_EXECUTED
           ORDER  BY vm.measureCode
           """)
    java.util.List<com.pfe.sageline.dtos.response.MissingMeasureDTO> missingMandatoryMeasures(
            @org.springframework.data.repository.query.Param("validationId") Long validationId);

    @org.springframework.data.jpa.repository.Query("""
           SELECT new com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO(
                  vm.measureCode, vm.measureLabel, vm.measuredValue,
                  vm.lowerBound, vm.upperBound, vm.deviationPct)
           FROM   ValidationMeasure vm
           WHERE  vm.validation.id = :validationId
             AND  vm.status        = com.pfe.sageline.enums.MeasureStatus.OUT_OF_RANGE
           ORDER  BY vm.measureCode
           """)
    java.util.List<com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO> outOfRangeMeasures(
            @org.springframework.data.repository.query.Param("validationId") Long validationId);
}
