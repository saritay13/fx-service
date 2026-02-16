package com.crewmeister.cmcodingchallenge.dto;

import java.time.LocalDate;
import java.util.List;

public record FxRateSeriesCollectionResponse(
        LocalDate start,
        LocalDate end,
        int count,
        List<FxRateSeriesResponse> series,
        List<String> warnings
) {}
