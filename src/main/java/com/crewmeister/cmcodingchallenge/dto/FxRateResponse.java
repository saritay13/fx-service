package com.crewmeister.cmcodingchallenge.dto;

import java.util.List;
import java.util.Map;

public record FxRateResponse(
        int totalCurrencies,
        List<FxRate> successfulRates,
        Map<String, String> failures
) {public int getSuccessCount() { return successfulRates.size(); }
    public int getFailureCount() { return failures.size(); }}
