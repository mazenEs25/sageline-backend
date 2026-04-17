package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecteurRequestDTO {

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 10, message = "Le code ne doit pas dépasser 10 caractères")
    private String code;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    private Boolean active = true;
}