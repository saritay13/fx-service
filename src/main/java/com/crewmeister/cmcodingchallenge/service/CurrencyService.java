package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.cache.CurrencyCache;
import com.crewmeister.cmcodingchallenge.dto.CurrencyInfo;
import com.crewmeister.cmcodingchallenge.dto.CurrencyListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class CurrencyService {

    private static Logger LOGGER = LoggerFactory.getLogger(CurrencyService.class);

    private final CurrencyCache currencyCache;
    private final BundesbankFxClient bundesbankFxClient;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public CurrencyService(CurrencyCache currencyCache, BundesbankFxClient bundesbankFxClient) {
        this.currencyCache = currencyCache;
        this.bundesbankFxClient = bundesbankFxClient;
    }

    public CurrencyListResponse getAllAvailableCurrencies() {
        LOGGER.info("Fetching All available Currencies");
        List<String> currencyCodes =  currencyCache.getOrLoad(CACHE_TTL, bundesbankFxClient::fetchAllAvailableCurrencies);

        List<CurrencyInfo> currencies = currencyCodes.stream()
                .map(this::toCurrencyInfo)
                .toList();

        return new CurrencyListResponse(currencies.size(), currencies);
    }

    private CurrencyInfo toCurrencyInfo(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return new CurrencyInfo(code, currency.getDisplayName(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Unknown currency code from upstream: {}", code);
            return new CurrencyInfo(code, "Unknown currency");
        }
    }
}