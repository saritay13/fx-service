package com.crewmeister.cmcodingchallenge.service;

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
public class CurrencyCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyCache.class);

    private final Clock clock;
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    private static class CacheEntry {
        final List<String> currencies;
        final Instant fetchedAt;

        CacheEntry(List<String> currencies, Instant fetchedAt) {
            this.currencies = currencies;
            this.fetchedAt = fetchedAt;
        }
    }

    public CurrencyCache(Clock clock) {
        this.clock = clock;
    }

    public List<String> getOrLoad(Duration ttl, Supplier<List<String>> loader) {
        CacheEntry entry = cache.get();

        if (entry != null && !isExpired(entry, ttl)) {
            LOGGER.info("Currency cache hit – loading currencies from cache");
            return entry.currencies;
        }

        if (entry == null) {
            LOGGER.info("Currency cache miss – loading currencies from upstream");
        } else {
            LOGGER.info("Currency cache expired (fetchedAt={}, ttl={}) – reloading", entry.fetchedAt, ttl);
        }
        List<String> fresh = loader.get();
        cache.set(new CacheEntry(fresh, Instant.now(clock)));

        LOGGER.info("Currency cache refreshed successfully (size={})", fresh.size());

        return fresh;
    }

    private boolean isExpired(CacheEntry entry, Duration ttl) {
        return entry.fetchedAt.plus(ttl).isBefore(Instant.now(clock));
    }
}

