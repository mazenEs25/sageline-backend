package com.pfe.sageline.repository;

import com.pfe.sageline.entity.MeasureCodeAlias;
import com.pfe.sageline.enums.PosteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeasureCodeAliasRepository extends JpaRepository<MeasureCodeAlias, Long> {
    Optional<MeasureCodeAlias> findByPosteTypeAndSourceCodeAndActiveTrue(PosteType posteType, String sourceCode);

    /** Reverse lookup: every source code that resolves to the given catalog code under a poste type. */
    List<MeasureCodeAlias> findByPosteTypeAndCatalogMeasureCodeAndActiveTrue(PosteType posteType, String catalogMeasureCode);
}
