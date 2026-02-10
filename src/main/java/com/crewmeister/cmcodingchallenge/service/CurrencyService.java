package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.cache.CurrencyCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class CurrencyService {

    Logger LOGGER = LoggerFactory.getLogger(CurrencyService.class);

    private final CurrencyCache currencyCache;
    private final BundesbankFxClient bundesbankFxClient;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public CurrencyService(CurrencyCache currencyCache, BundesbankFxClient bundesbankFxClient) {
        this.currencyCache = currencyCache;
        this.bundesbankFxClient = bundesbankFxClient;
    }

    public List<String> getAllAvailableCurrencies() {
        LOGGER.info("Fetching All available Currencies");
        return currencyCache.getOrLoad(CACHE_TTL, bundesbankFxClient::fetchAllAvailableCurrencies);
    }
}