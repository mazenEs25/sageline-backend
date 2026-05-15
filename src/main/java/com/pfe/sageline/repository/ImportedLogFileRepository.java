package com.pfe.sageline.repository;

import com.pfe.sageline.entity.ImportedLogFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportedLogFileRepository extends JpaRepository<ImportedLogFile, Long> {
    List<ImportedLogFile> findByValidationIdOrderByUploadedAtDesc(Long validationId);
}
