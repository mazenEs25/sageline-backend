package com.pfe.sageline.repository;

import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.PosteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PosteMeasureCatalogRepository extends JpaRepository<PosteMeasureCatalog, Long> {

    List<PosteMeasureCatalog> findByPosteTypeAndActiveOrderByDisplayOrder(
            PosteType posteType,
            boolean active
    );

    List<PosteMeasureCatalog> findByPosteTypeOrderByDisplayOrder(PosteType posteType);

    List<PosteMeasureCatalog> findByActive(boolean active);

    boolean existsByPosteTypeAndMeasureCodeAndActiveTrue(
            PosteType posteType,
            String measureCode
    );

    Optional<PosteMeasureCatalog> findByPosteTypeAndMeasureCodeAndActiveTrue(
            PosteType posteType,
            String measureCode
    );

    List<PosteMeasureCatalog> findAllByPosteTypeAndMeasureCodeInAndActiveTrue(
            PosteType posteType,
            Collection<String> measureCodes
    );

    @Query("SELECT DISTINCT p.posteType FROM PosteMeasureCatalog p WHERE p.active = true")
    List<PosteType> findDistinctActivePosteTypes();
}
