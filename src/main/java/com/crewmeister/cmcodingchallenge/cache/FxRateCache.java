package com.crewmeister.cmcodingchallenge.cache;

import com.crewmeister.cmcodingchallenge.domain.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.domain.FxRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class FxRateCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(FxRateCache.class);

    private final ConcurrentMap<CurrencyDateKey, FxRate> cache = new ConcurrentHashMap<>();

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
}

