package com.crewmeister.cmcodingchallenge.dto;

import java.time.LocalDate;
import java.util.List;

public record FxRateSeriesResponse(
        String baseCurrency,
        String currency,
        LocalDate start,
        LocalDate end,
        int count,
        List<FxRatePoint> rates
) {}
