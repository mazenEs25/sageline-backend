package com.pfe.sageline.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KPIResponse {

    private Long id;
    private String name;
    private Double value;
    private LocalDate calculationDate;
    private String productionLineName;
    private String productionLineCode;
    private LocalDateTime createdAt;
}