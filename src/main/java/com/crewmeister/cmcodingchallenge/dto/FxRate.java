package com.crewmeister.cmcodingchallenge.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FxRate(
        String baseCurrency,
        String currency,
        LocalDate date,
        BigDecimal rate,
        Instant fetchedAt
) {}
