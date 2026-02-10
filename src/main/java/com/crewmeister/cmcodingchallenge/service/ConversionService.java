package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.currency.CurrencyConversionRates;
import com.crewmeister.cmcodingchallenge.domain.FxRate;
import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ConversionService {

    Logger LOGGER = LoggerFactory.getLogger(ConversionService.class);

    private final FxRateService fxRateService;

    public ConversionService(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    private CurrencyConversionRates getEurFxRates(String currency, LocalDate date) {
        LOGGER.info("Fetching Rates for {} at {} ", currency, date);
        FxRate fxRate = fxRateService.getEurFxRate(currency, date);
        return new CurrencyConversionRates(fxRate.rate());
    }

    public ConversionResponse convertFxToEur(String currency, LocalDate date, BigDecimal amountFx) {
        CurrencyConversionRates rates = getEurFxRates(currency, date);

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

