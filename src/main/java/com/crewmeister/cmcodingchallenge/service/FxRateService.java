package com.crewmeister.cmcodingchallenge.service;


import com.crewmeister.cmcodingchallenge.cache.FxRateCache;
import com.crewmeister.cmcodingchallenge.dto.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.dto.FxRate;
import com.crewmeister.cmcodingchallenge.dto.FxRatePoint;
import com.crewmeister.cmcodingchallenge.dto.FxRateResponse;
import com.crewmeister.cmcodingchallenge.dto.FxRateSeriesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class FxRateService {

    Logger LOGGER = LoggerFactory.getLogger(FxRateService.class);
    public static final String EUR = "EUR";
    private final FxRateCache cache;
    private final BundesbankFxClient client;
    private final Clock clock;
    private final CurrencyService currencyService;

    public FxRateService(FxRateCache cache, BundesbankFxClient client, Clock clock, CurrencyService currencyService) {
        this.cache = cache;
        this.client = client;
        this.clock = clock;
        this.currencyService = currencyService;
    }


    public FxRateResponse getAllEurFxRatePerDate(LocalDate date) {

        Map<String, String> failures = new HashMap<>();

        List<FxRate> allEurFxRates = cache.getByDate(date).orElseGet(() -> {
            List<FxRate> rates =  client.fetchAllEurFxRateOnParticularDay(date).entrySet().stream()
                            .filter(e -> e.getValue().isPresent())
                            .map(e -> new FxRate(EUR, e.getKey(), date, e.getValue().get(), Instant.now(clock)))
                            .toList();
                    cache.putByDate(date, rates);
                    return rates;
                });

        FxRateResponse fxRateResponse = new FxRateResponse(allEurFxRates.size(), allEurFxRates, failures);
        return fxRateResponse;
    }

    public FxRate getEurFxRatePerDate(String currency, LocalDate date) {
        CurrencyDateKey key = new CurrencyDateKey(currency, date);

        return cache.get(key).orElseGet(() -> {
            Optional<BigDecimal> rate = client.fetchEurFxRateOnParticularDay(currency, date);
            if(!rate.isPresent())
                return null;
            FxRate fxRate = new FxRate(EUR, currency, date, rate.get(), Instant.now(clock));
            cache.put(key, fxRate);
            LOGGER.info("Fx Rate for currency {} is {}", currency, fxRate);
            return fxRate;
        });
    }

    public List<FxRateSeriesResponse> getSeriesByRange(LocalDate start, LocalDate end) {
        LOGGER.info("Fx Rate series for currency {} by Series Range start Date {} to end Date {} ", start, end);
        List<FxRateSeriesResponse> fxRateSeriesResponse = new ArrayList<>();

        Map<LocalDate, Map<String, Optional<BigDecimal>>>  allRatesByDate = client.fetchEurFxSeriesByRange( start, end);

        Set<String> currencies = allRatesByDate.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new)); // sorted

        List<FxRateSeriesResponse> responses = new ArrayList<>(currencies.size());

        for (String ccy : currencies) {
            List<FxRatePoint> points = allRatesByDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // ensure date order
                    .map(e -> {
                        LocalDate date = e.getKey();
                        Optional<BigDecimal> rateOpt = e.getValue().getOrDefault(ccy, Optional.empty());
                        return rateOpt.map(rate -> new FxRatePoint(date, rate)).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();
            fxRateSeriesResponse.add(getFxRateSeriesResponse(ccy, start, end, points));
        }

        return fxRateSeriesResponse;
    }

    public List<FxRateSeriesResponse> getLastNSeries(int lastN) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(lastN);
        return getSeriesByRange(start, end);
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

}

