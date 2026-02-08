package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.currency.CurrencyConversionRates;
import com.crewmeister.cmcodingchallenge.domain.FxRate;
import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ConversionService {

    private final FxRateService fxRateService;

    public ConversionService(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    private CurrencyConversionRates getRates(String currency, LocalDate date) {
        FxRate fxRate = fxRateService.getRate(currency, date);
        return new CurrencyConversionRates(fxRate.rate());
    }

    public ConversionResponse convertFxToEur(String currency, LocalDate date, BigDecimal amountFx) {
        CurrencyConversionRates rates = getRates(currency, date);

        BigDecimal rate = rates.getConversionRate();

        return new ConversionResponse(
                date,
                "EUR",
                currency,
                amountFx,
                rate,
                amountFx.divide(rate, 2, java.math.RoundingMode.HALF_UP)
        );


    }
}

