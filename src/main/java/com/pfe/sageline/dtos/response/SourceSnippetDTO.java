package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.LogFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceSnippetDTO {
    private Long measureId;
    private String originalFilename;
    private LogFormat detectedFormat;
    private String snippet;
    private Boolean available;
    private Integer startLine;
    private Integer endLine;
}
