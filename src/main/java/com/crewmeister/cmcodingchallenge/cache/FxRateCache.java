package com.crewmeister.cmcodingchallenge.cache;

import com.crewmeister.cmcodingchallenge.dto.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.dto.FxRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    /** Test helper */
    public void clear() {
        cache.clear();
        ratesByDate.clear();
        LOGGER.debug("FX cache cleared");
    }

}

