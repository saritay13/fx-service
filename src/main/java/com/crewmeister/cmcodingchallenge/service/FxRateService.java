package com.crewmeister.cmcodingchallenge.service;


import com.crewmeister.cmcodingchallenge.cache.FxRateCache;
import com.crewmeister.cmcodingchallenge.domain.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.domain.FxRate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class FxRateService {

    private final FxRateCache cache;
    private final BundesbankFxClient client;
    private final Clock clock;

    public FxRateService(FxRateCache cache, BundesbankFxClient client, Clock clock) {
        this.cache = cache;
        this.client = client;
        this.clock = clock;
    }

    public FxRate getRate(String currencyRaw, LocalDate date) {
        String currency = normalizeCurrency(currencyRaw);
        CurrencyDateKey key = new CurrencyDateKey(currency, date);

        return cache.get(key).orElseGet(() -> {
            BigDecimal rate = client.fetchEurFxRate(currency, date);
            FxRate fxRate = new FxRate("EUR", currency, date, rate, Instant.now(clock));
            cache.put(key, fxRate);
            return fxRate;
        });
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}

