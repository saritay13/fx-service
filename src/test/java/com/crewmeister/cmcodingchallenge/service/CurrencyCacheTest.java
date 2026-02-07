package com.crewmeister.cmcodingchallenge.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyCacheTest {

    public static final String USD = "USD";
    public static final String EUR = "EUR";
    public static final int ONE = 1;

    @Test
    void returnsCachedValueWithinTtl() {

        Instant initial_time = Instant.now();
        Clock fixedClock = Clock.fixed(initial_time, ZoneOffset.UTC);

        CurrencyCache cache = new CurrencyCache(fixedClock);
        Duration ttl = Duration.ofHours(24);

        AtomicInteger calls = new AtomicInteger(0);

        List<String> first = cache.getOrLoad(ttl, () -> {
            calls.incrementAndGet();
            return List.of(USD, EUR);
        });

        List<String> second = cache.getOrLoad(ttl, () -> {
            calls.incrementAndGet();
            return List.of("SHOULD_NOT_HAPPEN");
        });

        assertThat(first).containsExactly(USD, EUR);
        assertThat(second).containsExactly(USD, EUR);
        assertThat(calls.get()).isEqualTo(ONE);
    }

    @Test
    void reloadsWhenExpired() throws InterruptedException {
        Instant initial_time = Instant.now();
        Clock fixedClock = Clock.fixed(initial_time, ZoneOffset.UTC);

        CurrencyCache cache = new CurrencyCache(fixedClock);

        List<String> first = cache.getOrLoad(Duration.ofSeconds(10), () -> List.of(USD));

        Clock laterClock = Clock.fixed(initial_time.plusSeconds(11), ZoneOffset.UTC);
        cache = new CurrencyCache(laterClock);

        List<String> second = cache.getOrLoad(Duration.ofSeconds(10), () -> List.of(EUR));

        assertThat(first).containsExactly(USD);
        assertThat(second).containsExactly(EUR);

    }
}

