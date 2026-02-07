package com.crewmeister.cmcodingchallenge.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CurrencyService {

    private final CurrencyCache currencyCache;
    private final BundesbankFxClient bundesbankFxClient;

    // Start simple: refresh every 24h
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public CurrencyService(CurrencyCache currencyCache, BundesbankFxClient bundesbankFxClient) {
        this.currencyCache = currencyCache;
        this.bundesbankFxClient = bundesbankFxClient;
    }

    public List<String> getCurrencies() {
        return currencyCache.getOrLoad(CACHE_TTL, bundesbankFxClient::fetchCurrencies);
    }
}