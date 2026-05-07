package com.pfe.sageline.dtos.request;

public record HandoverInitiateRequest(
        String handoverNote,
        String progressSummary
) {}
