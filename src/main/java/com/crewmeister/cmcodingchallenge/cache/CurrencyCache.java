package com.crewmeister.cmcodingchallenge.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class CurrencyCache{

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyCache.class);

    private final Clock clock;
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    private record CacheEntry (List<String> currencies, Instant fetchedAt){}

    public CurrencyCache(Clock clock) {
        this.clock = clock;
    }

    public List<String> getOrLoad(Duration ttl, Supplier<List<String>> loader) {
        return cache.updateAndGet(current -> {
            if (current != null && !isExpired(current, ttl)) {
                LOGGER.debug("Currency cache hit");
                return current;
            }

            LOGGER.info("Currency cache miss - loading from upstream");
            List<String> fresh = loader.get();
            LOGGER.info("Currency cache refreshed (size={})", fresh.size());
            return new CacheEntry(fresh, Instant.now(clock));
        }).currencies;
    }

    private boolean isExpired(CacheEntry entry, Duration ttl) {
        return entry.fetchedAt.plus(ttl).isBefore(Instant.now(clock));
    }
}

