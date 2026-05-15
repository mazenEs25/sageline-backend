package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.LogFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogImportReportDTO {
    private LogFormat detectedFormat;
    private Integer totalParsed;
    private List<MatchedMeasureDTO> matched;
    private List<UnmatchedMeasureDTO> unmatched;
    private List<WouldOverwriteMeasureDTO> wouldOverwrite;
    private List<WarningDTO> warnings;
    private Long ticketId;
    private Boolean dryRun;
}
