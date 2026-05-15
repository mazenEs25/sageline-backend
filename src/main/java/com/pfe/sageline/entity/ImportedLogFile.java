package com.pfe.sageline.entity;

import com.pfe.sageline.enums.LogFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "imported_log_file")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ImportedLogFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "detected_format", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private LogFormat detectedFormat;

    @Column(name = "stored_path", nullable = false, length = 512)
    private String storedPath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreatedDate
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "overwrite_existing", nullable = false)
    private Boolean overwriteExisting;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
