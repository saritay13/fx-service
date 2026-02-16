package com.crewmeister.cmcodingchallenge.dto;

import java.util.List;

public record CurrencyListResponse(
        int count,
        List<CurrencyInfo> currencies
) {}
