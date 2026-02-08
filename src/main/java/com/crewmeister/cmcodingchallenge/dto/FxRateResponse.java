package com.crewmeister.cmcodingchallenge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRateResponse(
        LocalDate date,
        String baseCurrency,
        String currency,
        BigDecimal rate,
        String meaning
) {}

