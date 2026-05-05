package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Payload for PATCH /api/validations/{id}/postes/{zoneId}/complete.
 * Marks a single poste inside a line-ticket as CONFORME or NON_CONFORME.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosteCompleteRequestDTO {

    /** Must be "CONFORME" or "NON_CONFORME". */
    @NotNull(message = "Le statut final du poste est obligatoire")
    private String finalStatus;

    private String notes;
}
