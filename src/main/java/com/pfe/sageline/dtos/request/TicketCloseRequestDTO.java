package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCloseRequestDTO {

    @NotNull(message = "Le statut final est obligatoire")
    private String finalStatus; // CONFORME or NON_CONFORME

    private String reviewComments;
}