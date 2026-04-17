package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.response.KPIResponse;
import com.pfe.sageline.entity.KPI;
import org.springframework.stereotype.Component;

@Component
public class KPIMapper {

    public KPIResponse toResponseDTO(KPI kpi) {
        if (kpi == null) {
            return null;
        }

        KPIResponse dto = new KPIResponse();
        dto.setId(kpi.getId());
        dto.setName(kpi.getName());
        dto.setValue(kpi.getValue());
        dto.setCalculationDate(kpi.getCalculationDate());

        if (kpi.getProductionLine() != null) {
            dto.setProductionLineName(kpi.getProductionLine().getName());
            dto.setProductionLineCode(kpi.getProductionLine().getCode());
        }

        dto.setCreatedAt(kpi.getCreatedAt());

        return dto;
    }
}