package com.pfe.sageline.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketWeekPlanRequestDTO {

    @NotNull(message = "La date de début de semaine est obligatoire")
    private LocalDate weekStart;

    @NotNull(message = "La date de fin de semaine est obligatoire")
    private LocalDate weekEnd;

    @NotEmpty(message = "Au moins un ticket est requis")
    @Valid
    private List<TicketCreateRequestDTO> tickets;
}