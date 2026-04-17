package com.pfe.sageline.repository;

import com.pfe.sageline.entity.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhaseRepository extends JpaRepository<Phase, Long> {

    List<Phase> findBySecteurId(Long secteurId);

    List<Phase> findBySecteurIdAndActiveTrue(Long secteurId);

    Optional<Phase> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT p FROM Phase p JOIN FETCH p.secteur WHERE p.id = :id")
    Optional<Phase> findByIdWithSecteur(@Param("id") Long id);

    List<Phase> findByActiveTrue();

    List<Phase> findAllByOrderBySecteurIdAscOrderIndexAsc();
}