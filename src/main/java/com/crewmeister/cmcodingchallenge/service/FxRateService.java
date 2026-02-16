package com.crewmeister.cmcodingchallenge.service;


import com.crewmeister.cmcodingchallenge.cache.FxRateCache;
import com.crewmeister.cmcodingchallenge.dto.*;
import com.crewmeister.cmcodingchallenge.error.RateNotFoundException;
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

    private static Logger LOGGER = LoggerFactory.getLogger(FxRateService.class);
    public static final String EUR = "EUR";
    private final FxRateCache cache;
    private final BundesbankFxClient client;
    private final Clock clock;

    public FxRateService(FxRateCache cache, BundesbankFxClient client, Clock clock) {
        this.cache = cache;
        this.client = client;
        this.clock = clock;
    }


    public FxRateResponse getAllEurFxRatePerDate(LocalDate date) {



        Optional<List<FxRate>> cachedRates = cache.getByDate(date);
        if (cachedRates.isPresent()) {
            Map<String, String> failures = new HashMap<>();
            List<FxRate> rates = cachedRates.get();
            if (rates.isEmpty()) {
                failures.put("NO_DATA", "No EUR-FX rates available for date " + date);
            }
            return new FxRateResponse(rates.size(), rates, failures);
        }

        Map<String, Optional<BigDecimal>> fetchedRates = client.fetchAllEurFxRateOnParticularDay(date);

        List<FxRate> successfulRates = fetchedRates.entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .map(e -> new FxRate(EUR, e.getKey(), date, e.getValue().get(), Instant.now(clock)))
                .toList();


        Map<String, String> failures = fetchedRates.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> "Rate not available for " + e.getKey() + " on " + date,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        if (successfulRates.isEmpty() && failures.isEmpty()) {
            failures.put("NO_DATA", "No EUR-FX rates available for date " + date);
        }

        cache.putByDate(date, successfulRates);
        return new FxRateResponse(successfulRates.size(), successfulRates, failures);
    }

    public FxRate getEurFxRatePerDate(String currency, LocalDate date) {
        CurrencyDateKey key = new CurrencyDateKey(currency, date);

        return cache.get(key).orElseGet(() -> {
            Optional<BigDecimal> rate = client.fetchEurFxRateOnParticularDay(currency, date);
            if (rate.isEmpty()) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }
            FxRate fxRate = new FxRate(EUR, currency, date, rate.get(), Instant.now(clock));
            cache.put(key, fxRate);
            LOGGER.info("Fx Rate for currency {} is {}", currency, fxRate);
            return fxRate;
        });
    }

    public FxRateSeriesCollectionResponse getSeriesByRange(LocalDate start, LocalDate end) {
        LOGGER.info("Fx Rate series by date range start={} end={}", start, end);
        List<FxRateSeriesResponse> fxRateSeriesResponse = new ArrayList<>();

        Optional<List<FxRateSeriesResponse>> cachedSeries = cache.getSeriesByRange(start, end);
        if (cachedSeries.isPresent()) {
            return buildSeriesCollectionResponse(start, end, cachedSeries.get());
        }

        Map<LocalDate, Map<String, Optional<BigDecimal>>>  allRatesByDate = client.fetchEurFxSeriesByRange( start, end);

        Set<String> currencies = allRatesByDate.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

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
        List<FxRateSeriesResponse> immutableSeries = List.copyOf(fxRateSeriesResponse);
        cache.putSeriesByRange(start, end, immutableSeries);
        return buildSeriesCollectionResponse(start, end, immutableSeries);
    }

    public FxRateSeriesCollectionResponse getLastNSeries(int lastN) {

        if (lastN <= 0) {
            throw new IllegalArgumentException("lastN must be positive");
        }
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(lastN - 1L);

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

    private FxRateSeriesCollectionResponse buildSeriesCollectionResponse(LocalDate start, LocalDate end, List<FxRateSeriesResponse> series) {
        List<String> warnings = series.isEmpty()
                ? List.of("No EUR-FX rates available between " + start + " and " + end)
                : List.of();

        return new FxRateSeriesCollectionResponse(
                start,
                end,
                series.size(),
                series,
                warnings
        );
    }

}

