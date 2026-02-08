package com.crewmeister.cmcodingchallenge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionResponse(
        LocalDate date,
        String baseCurrency,
        String currency,
        BigDecimal originalAmount,
        BigDecimal rate,
        BigDecimal convertedAmountEur
) {}
