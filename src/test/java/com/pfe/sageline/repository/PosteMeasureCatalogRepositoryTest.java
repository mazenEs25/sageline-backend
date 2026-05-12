package com.pfe.sageline.repository;

import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses {@link SpringBootTest} (rather than {@code @DataJpaTest}) so that the
 * test profile's {@link com.pfe.sageline.testsupport.CatalogSchemaInitializer}
 * loads the partial unique index and CHECK constraint that Hibernate cannot
 * express on its own. {@code @Transactional} rolls each test back to isolate them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
public class PosteMeasureCatalogRepositoryTest extends PostgresTestcontainer {

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired
    private PosteMeasureCatalogRepository repository;

    private PosteMeasureCatalog createSampleEntity() {
        return PosteMeasureCatalog.builder()
            .posteType(PosteType.TEST_FONCTIONNEL)
            .measureCode("TEST_CODE_001")
            .measureLabel("Test Label")
            .category(MeasureCategory.POWER)
            .defaultUnit("dBm")
            .defaultLowerBound(-20.0)
            .defaultUpperBound(10.0)
            .mandatory(true)
            .displayOrder(1)
            .active(true)
            .build();
    }

    @Test
    @WithMockUser(username = "42")
    public void savingRowPopulatesAuditColumns() {
        PosteMeasureCatalog entity = createSampleEntity();

        PosteMeasureCatalog saved = repository.save(entity);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getCreatedBy());
        assertNotNull(saved.getUpdatedAt());
        assertNotNull(saved.getUpdatedBy());
        assertEquals(42L, saved.getCreatedBy());
        assertEquals(42L, saved.getUpdatedBy());
    }

    @Test
    @WithMockUser(username = "42")
    public void updatingRowUpdatesTimestampButPreservesCreationFields() throws InterruptedException {
        PosteMeasureCatalog entity = createSampleEntity();
        PosteMeasureCatalog saved = repository.save(entity);

        Instant originalCreatedAt = saved.getCreatedAt();
        Long originalCreatedBy = saved.getCreatedBy();

        Thread.sleep(100); // Small delay to ensure timestamp difference

        saved.setMeasureLabel("Updated Label");
        PosteMeasureCatalog updated = repository.save(saved);

        assertEquals(originalCreatedAt, updated.getCreatedAt(), "createdAt should not change");
        assertEquals(originalCreatedBy, updated.getCreatedBy(), "createdBy should not change");
        assertTrue(updated.getUpdatedAt().isAfter(originalCreatedAt), "updatedAt should be newer");
    }

    @Test
    public void duplicateActiveRowsViolateUniqueIndex() {
        PosteMeasureCatalog first = createSampleEntity();
        repository.save(first);

        PosteMeasureCatalog duplicate = PosteMeasureCatalog.builder()
            .posteType(PosteType.TEST_FONCTIONNEL)
            .measureCode("TEST_CODE_001")
            .measureLabel("Different Label")
            .category(MeasureCategory.VOLTAGE)
            .defaultUnit("V")
            .defaultLowerBound(-100.0)
            .defaultUpperBound(100.0)
            .mandatory(false)
            .displayOrder(2)
            .active(true)
            .build();

        assertThrows(DataIntegrityViolationException.class, () -> repository.save(duplicate));
    }

    @Test
    public void softDeletedRowPlusNewActiveLowRowBothPersist() {
        PosteMeasureCatalog first = createSampleEntity();
        first.setActive(false);
        repository.save(first);

        PosteMeasureCatalog second = PosteMeasureCatalog.builder()
            .posteType(PosteType.TEST_FONCTIONNEL)
            .measureCode("TEST_CODE_001")
            .measureLabel("New Active Row")
            .category(MeasureCategory.POWER)
            .defaultUnit("dBm")
            .defaultLowerBound(-20.0)
            .defaultUpperBound(10.0)
            .mandatory(true)
            .displayOrder(1)
            .active(true)
            .build();

        PosteMeasureCatalog saved = repository.save(second);

        assertNotNull(saved.getId());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    public void checkConstraintRejectsInvertedBounds() {
        PosteMeasureCatalog entity = PosteMeasureCatalog.builder()
            .posteType(PosteType.TEST_FONCTIONNEL)
            .measureCode("BAD_BOUNDS")
            .measureLabel("Bad Bounds")
            .category(MeasureCategory.POWER)
            .defaultUnit("dBm")
            .defaultLowerBound(10.0)
            .defaultUpperBound(-20.0)
            .mandatory(true)
            .displayOrder(1)
            .active(true)
            .build();

        assertThrows(DataIntegrityViolationException.class, () -> repository.save(entity));
    }
}
