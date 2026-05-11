package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.PosteMeasureCatalogRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogUpdateRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.BoundsViolationException;
import com.pfe.sageline.exception.DuplicateCatalogTemplateException;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.PosteMeasureCatalogMapper;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PosteCatalogServiceUnitTest {

    @Mock
    private PosteMeasureCatalogRepository repository;

    @Mock
    private PosteMeasureCatalogMapper mapper;

    @InjectMocks
    private PosteCatalogServiceImpl service;

    private PosteMeasureCatalog createSampleEntity() {
        return PosteMeasureCatalog.builder()
            .id(1L)
            .posteType(PosteType.TEST_FONCTIONNEL)
            .measureCode("TEST_CODE")
            .measureLabel("Test Label")
            .category(MeasureCategory.POWER)
            .defaultUnit("dBm")
            .defaultLowerBound(-20.0)
            .defaultUpperBound(10.0)
            .mandatory(true)
            .displayOrder(1)
            .active(true)
            .createdAt(Instant.now())
            .createdBy(1L)
            .updatedAt(Instant.now())
            .updatedBy(1L)
            .build();
    }

    @Test
    public void listAll_noFilter_noInactive() {
        PosteMeasureCatalog entity = createSampleEntity();
        PosteMeasureCatalogResponse response = new PosteMeasureCatalogResponse(
            1L, PosteType.TEST_FONCTIONNEL, "TEST_CODE", "Test Label", MeasureCategory.POWER,
            "dBm", -20.0, 10.0, true, 1, true, null, null, null,
            Instant.now(), 1L, Instant.now(), 1L
        );

        when(repository.findByActive(true)).thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(response));

        List<PosteMeasureCatalogResponse> result = service.listAll(null, false);

        assertEquals(1, result.size());
        verify(repository, times(1)).findByActive(true);
    }

    @Test
    public void listAll_withFilter_noInactive() {
        PosteMeasureCatalog entity = createSampleEntity();
        PosteMeasureCatalogResponse response = new PosteMeasureCatalogResponse(
            1L, PosteType.TEST_FONCTIONNEL, "TEST_CODE", "Test Label", MeasureCategory.POWER,
            "dBm", -20.0, 10.0, true, 1, true, null, null, null,
            Instant.now(), 1L, Instant.now(), 1L
        );

        when(repository.findByPosteTypeAndActiveOrderByDisplayOrder(PosteType.TEST_FONCTIONNEL, true))
            .thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(response));

        List<PosteMeasureCatalogResponse> result = service.listAll(PosteType.TEST_FONCTIONNEL, false);

        assertEquals(1, result.size());
        verify(repository, times(1)).findByPosteTypeAndActiveOrderByDisplayOrder(PosteType.TEST_FONCTIONNEL, true);
    }

    @Test
    public void getById_found() {
        PosteMeasureCatalog entity = createSampleEntity();
        PosteMeasureCatalogResponse response = new PosteMeasureCatalogResponse(
            1L, PosteType.TEST_FONCTIONNEL, "TEST_CODE", "Test Label", MeasureCategory.POWER,
            "dBm", -20.0, 10.0, true, 1, true, null, null, null,
            Instant.now(), 1L, Instant.now(), 1L
        );

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        PosteMeasureCatalogResponse result = service.getById(1L);

        assertNotNull(result);
        assertEquals("TEST_CODE", result.measureCode());
    }

    @Test
    public void getById_notFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(999L));
    }

    @Test
    public void create_happy_path() {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "NEW_CODE",
            "New Label",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        PosteMeasureCatalog entity = createSampleEntity();
        entity.setMeasureCode("NEW_CODE");
        PosteMeasureCatalogResponse response = new PosteMeasureCatalogResponse(
            1L, PosteType.TEST_FONCTIONNEL, "NEW_CODE", "New Label", MeasureCategory.POWER,
            "dBm", -20.0, 10.0, true, 1, true, null, null, null,
            Instant.now(), 1L, Instant.now(), 1L
        );

        when(repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(PosteType.TEST_FONCTIONNEL, "NEW_CODE"))
            .thenReturn(false);
        when(repository.save(any(PosteMeasureCatalog.class))).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);

        PosteMeasureCatalogResponse result = service.create(request);

        assertNotNull(result);
        assertEquals("NEW_CODE", result.measureCode());
        verify(repository, times(1)).save(any(PosteMeasureCatalog.class));
    }

    @Test
    public void create_duplicate_throws() {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "DUP_CODE",
            "Dup Label",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        when(repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(PosteType.TEST_FONCTIONNEL, "DUP_CODE"))
            .thenReturn(true);

        assertThrows(DuplicateCatalogTemplateException.class, () -> service.create(request));
    }

    @Test
    public void create_invertedBounds_throws() {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "BAD_CODE",
            "Bad Label",
            MeasureCategory.POWER,
            "dBm",
            10.0,
            -20.0,
            true,
            1,
            null,
            null,
            null
        );

        assertThrows(BoundsViolationException.class, () -> service.create(request));
    }

    @Test
    public void softDelete_happy_path() {
        PosteMeasureCatalog entity = createSampleEntity();
        entity.setActive(true);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PosteMeasureCatalog.class))).thenReturn(entity);

        service.softDelete(1L);

        verify(repository, times(1)).save(any(PosteMeasureCatalog.class));
    }

    @Test
    public void softDelete_alreadyInactive_noOp() {
        PosteMeasureCatalog entity = createSampleEntity();
        entity.setActive(false);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.softDelete(1L);

        verify(repository, never()).save(any(PosteMeasureCatalog.class));
    }

    @Test
    public void listPostesWithActive() {
        when(repository.findDistinctActivePosteTypes())
            .thenReturn(List.of(PosteType.TEST_FONCTIONNEL, PosteType.WIFI_CONDUIT));

        List<PosteType> result = service.listPostesWithActive();

        assertEquals(2, result.size());
        verify(repository, times(1)).findDistinctActivePosteTypes();
    }
}
