package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhaseRequestDTO {

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 20)
    private String code;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Le secteur est obligatoire")
    private Long secteurId;

    @NotNull(message = "L'ordre est obligatoire")
    private Integer orderIndex;

    private Boolean active = true;
}