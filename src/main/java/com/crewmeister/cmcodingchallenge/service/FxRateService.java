package com.crewmeister.cmcodingchallenge.service;


import com.crewmeister.cmcodingchallenge.cache.FxRateCache;
import com.crewmeister.cmcodingchallenge.domain.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.domain.FxRate;
import com.crewmeister.cmcodingchallenge.dto.FxRatePoint;
import com.crewmeister.cmcodingchallenge.dto.FxRateSeriesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class FxRateService {

    Logger LOGGER = LoggerFactory.getLogger(FxRateService.class);
    public static final String EUR = "EUR";
    private final FxRateCache cache;
    private final BundesbankFxClient client;
    private final Clock clock;

    public FxRateService(FxRateCache cache, BundesbankFxClient client, Clock clock) {
        this.cache = cache;
        this.client = client;
        this.clock = clock;
    }

    public FxRate getEurFxRate(String currency, LocalDate date) {
        CurrencyDateKey key = new CurrencyDateKey(currency, date);

        return cache.get(key).orElseGet(() -> {
            BigDecimal rate = client.fetchEurFxRateOnParticularDay(currency, date);
            FxRate fxRate = new FxRate(EUR, currency, date, rate, Instant.now(clock));
            cache.put(key, fxRate);
            LOGGER.info("Fx Rate for currency {} is {}", currency, fxRate);
            return fxRate;
        });
    }

    public FxRateSeriesResponse getSeriesByRange(String currency, LocalDate start, LocalDate end) {
        LOGGER.info("Fx Rate series for currency {} by Series Range start Date {} to end Date {} ", currency, start, end);
        List<FxRatePoint> points = client.fetchEurFxSeriesByRange(currency, start, end);

        return getFxRateSeriesResponse(currency, start, end, points);
    }

    public FxRateSeriesResponse getLastNSeries(String currency, int lastN) {

        LOGGER.info("Fx Rate series for currency {} last {} series ", currency, lastN);
        List<FxRatePoint> points = client.fetchEurFxSeriesLastN(currency, lastN);

        LocalDate start = points.isEmpty() ? null : points.get(0).date();
        LocalDate end = points.isEmpty() ? null : points.get(points.size() - 1).date();

        return getFxRateSeriesResponse(currency, start, end, points);
    }

    private static FxRateSeriesResponse getFxRateSeriesResponse(String currency, LocalDate start, LocalDate end, List<FxRatePoint> points) {
        return new FxRateSeriesResponse(
                EUR,
                currency,
                start,
                end,
                points.size(),
                points
        );
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}

