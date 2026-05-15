package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.response.SourceSnippetDTO;
import com.pfe.sageline.service.Import.SourceSnippetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/validations/{validationId}/measures/{measureId}")
@Slf4j
public class MeasureSourceController {

    private final SourceSnippetService snippetService;

    public MeasureSourceController(SourceSnippetService snippetService) {
        this.snippetService = snippetService;
    }

    @GetMapping("/source-snippet")
    @PreAuthorize("hasAnyRole('TECH_VAL', 'TECH_PREP', 'ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    public ResponseEntity<SourceSnippetDTO> getSourceSnippet(
            @PathVariable Long validationId,
            @PathVariable Long measureId) {

        SourceSnippetDTO snippet = snippetService.snippet(validationId, measureId);
        return ResponseEntity.ok(snippet);
    }
}
