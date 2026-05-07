package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.HandoverAssignRequest;
import com.pfe.sageline.dtos.request.HandoverInitiateRequest;
import com.pfe.sageline.dtos.response.HandoverKpiResponse;
import com.pfe.sageline.dtos.response.HandoverResponse;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TriggerType;

import java.time.LocalDate;
import java.util.List;

public interface HandoverService {

    HandoverResponse initiateHandover(Long validationId, HandoverInitiateRequest req, TriggerType trigger);

    void triggerAutoHandover(Validation ticket);

    HandoverResponse acceptHandover(Long handoverId);

    HandoverResponse assignHandover(Long handoverId, HandoverAssignRequest req);

    HandoverResponse cancelHandover(Long handoverId);

    List<HandoverResponse> getHandoverHistory(Long validationId);

    List<HandoverResponse> getPendingHandovers();

    HandoverKpiResponse getHandoverKpis(LocalDate from, LocalDate to);

    void autoCancelIfPending(Validation validation);
}
