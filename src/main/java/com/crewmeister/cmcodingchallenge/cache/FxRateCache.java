package com.crewmeister.cmcodingchallenge.cache;

import com.crewmeister.cmcodingchallenge.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class FxRateCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FxRateCache.class);

    private final ConcurrentMap<CurrencyDateKey, FxRate> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<LocalDate, List<FxRate>> ratesByDate = new ConcurrentHashMap<>();
    private final ConcurrentMap<SeriesRangeKey, CachedSeries> seriesByRange = new ConcurrentHashMap<>();
    private final Clock clock;
    private static final Duration SERIES_CACHE_TTL = Duration.ofHours(10);

    public FxRateCache(Clock clock) {
        this.clock = clock;
    }

    public Optional<FxRate> get(CurrencyDateKey key) {
        FxRate v = cache.get(key);
        if (v != null) {
            LOGGER.debug("FX rate cache hit: {}", key);
        }
        return Optional.ofNullable(v);
    }

    public void put(CurrencyDateKey key, FxRate value) {
        cache.put(key, value);
        LOGGER.debug("FX rate cache put: {}", key);
    }

    public Optional<List<FxRate>> getByDate(LocalDate date) {
        List<FxRate> list = ratesByDate.get(date);
        if (list != null) {
            LOGGER.debug("FX date cache hit: {}", date);
            return Optional.of(list);
        }
        return Optional.empty();
    }

    public void putByDate(LocalDate date, List<FxRate> rates) {
        ratesByDate.put(date, List.copyOf(rates));
        populateCurrencyCache(date, rates);
        LOGGER.debug("FX date cache put: {} -> {} rates", date, rates.size());
    }

    public void populateCurrencyCache(LocalDate date, List<FxRate> rates) {
        // populate per-currency cache
        for (FxRate rate : rates) {
            CurrencyDateKey key = new CurrencyDateKey(rate.currency(), rate.date());
            cache.put(key, rate);
        }
        LOGGER.debug("FX cache bulk put for date {} with {} rates", date, rates.size());
    }

    public Optional<List<FxRateSeriesResponse>> getSeriesByRange(LocalDate start, LocalDate end) {
        SeriesRangeKey key = new SeriesRangeKey(start, end);
        CachedSeries cached = seriesByRange.get(key);
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.expiresAt().isBefore(Instant.now(clock))) {
            seriesByRange.remove(key);
            return Optional.empty();
        }

        LOGGER.debug("FX series cache hit: {}", key);
        return Optional.of(cached.series());
    }

    public void putSeriesByRange(LocalDate start, LocalDate end, List<FxRateSeriesResponse> series) {
        SeriesRangeKey key = new SeriesRangeKey(start, end);
        seriesByRange.put(key, new CachedSeries(List.copyOf(series), Instant.now(clock).plus(SERIES_CACHE_TTL)));
        LOGGER.debug("FX series cache put: {} -> {} series", key, series.size());
    }

    private record SeriesRangeKey(LocalDate start, LocalDate end) {}

    private record CachedSeries(List<FxRateSeriesResponse> series, Instant expiresAt) {}
}

