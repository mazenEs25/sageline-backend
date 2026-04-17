package com.pfe.sageline.repository;

import com.pfe.sageline.entity.Secteur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface SecteurRepository extends JpaRepository<Secteur, Long> {

    Optional<Secteur> findByCode(String code);

    List<Secteur> findByActiveTrue();

    boolean existsByCode(String code);
}