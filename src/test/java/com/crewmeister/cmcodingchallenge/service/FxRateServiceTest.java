package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.cache.FxRateCache;
import com.crewmeister.cmcodingchallenge.dto.CurrencyDateKey;
import com.crewmeister.cmcodingchallenge.dto.FxRate;
import com.crewmeister.cmcodingchallenge.dto.FxRateResponse;
import com.crewmeister.cmcodingchallenge.dto.FxRateSeriesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FxRateServiceTest {

    @Mock
    FxRateCache cache;
    @Mock BundesbankFxClient client;
    @Mock CurrencyService currencyService; // not used in current service methods, but required for ctor

    private Clock fixedClock;
    private FxRateService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2024-01-10T10:15:30Z"), ZoneOffset.UTC);
        service = new FxRateService(cache, client, fixedClock, currencyService);
    }

    static List<Arguments> eurFxRate_cases_cacheMiss_clientReturnsRate() {
        return List.of(
                Arguments.of("USD", LocalDate.of(2024, 1, 10), new BigDecimal("1.10")),
                Arguments.of("GBP", LocalDate.of(2024, 1, 10), new BigDecimal("0.86")),
                Arguments.of("CHF", LocalDate.of(2024, 2,  5), new BigDecimal("0.95"))
        );
    }

    @ParameterizedTest
    @MethodSource("eurFxRate_cases_cacheMiss_clientReturnsRate")
    void getEurFxRatePerDate_cacheMiss_clientHasRate_returnsFxRate_andCaches(
            String currency, LocalDate date, BigDecimal expectedRate
    ) {
        CurrencyDateKey key = new CurrencyDateKey(currency, date);

        when(cache.get(key)).thenReturn(Optional.empty());
        when(client.fetchEurFxRateOnParticularDay(currency, date)).thenReturn(Optional.of(expectedRate));

        FxRate fxRate = service.getEurFxRatePerDate(currency, date);

        assertNotNull(fxRate);
        assertEquals(currency, fxRate.currency());
        assertEquals(date, fxRate.date());
        assertEquals(expectedRate, fxRate.rate());
        assertEquals(Instant.now(fixedClock), fxRate.fetchedAt());

        verify(cache).put(eq(key), eq(fxRate));
        verify(client).fetchEurFxRateOnParticularDay(currency, date);
    }

    static Stream<Arguments> getAllEurFxRate_simpleCases() {
        return Stream.of(
                Arguments.of(
                        LocalDate.of(2024, 1, 10),
                        Map.of(
                                "USD", Optional.of(new BigDecimal("1.10")),
                                "GBP", Optional.of(new BigDecimal("0.86"))
                        ),
                        2
                ),
                Arguments.of(
                        LocalDate.of(2024, 2, 5),
                        Map.of(
                                "CHF", Optional.of(new BigDecimal("0.95"))
                        ),
                        1
                )
        );
    }

    @ParameterizedTest
    @MethodSource("getAllEurFxRate_simpleCases")
    void getAllEurFxRatePerDate_cacheMiss_returnsRates_andCaches(
            LocalDate date,
            Map<String, Optional<BigDecimal>> clientResponse,
            int expectedCount
    ) {
        // given
        when(cache.getByDate(date)).thenReturn(Optional.empty());
        when(client.fetchAllEurFxRateOnParticularDay(date)).thenReturn(clientResponse);

        // when
        FxRateResponse response = service.getAllEurFxRatePerDate(date);

        // then
        assertNotNull(response);
        assertEquals(expectedCount, response.getSuccessCount());
        assertEquals(expectedCount, response.successfulRates().size());

        // basic sanity check on first item
        FxRate rate = response.successfulRates().get(0);
        assertEquals(FxRateService.EUR, rate.baseCurrency());
        assertEquals(date, rate.date());

        verify(cache).putByDate(eq(date), anyList());
        verify(client).fetchAllEurFxRateOnParticularDay(date);
    }

    static Stream<Arguments> getSeriesByRange_simpleCases() {
        return Stream.of(
                Arguments.of(
                        LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 1, 3),
                        Map.of(
                                LocalDate.of(2024, 1, 1), Map.of(
                                        "USD", Optional.of(new BigDecimal("1.10")),
                                        "GBP", Optional.of(new BigDecimal("0.85"))
                                ),
                                LocalDate.of(2024, 1, 2), Map.of(
                                        "USD", Optional.of(new BigDecimal("1.20"))
                                )
                        ),
                        2 // USD + GBP
                ),
                Arguments.of(
                        LocalDate.of(2024, 2, 1),
                        LocalDate.of(2024, 2, 2),
                        Map.of(
                                LocalDate.of(2024, 2, 1), Map.of(
                                        "CHF", Optional.of(new BigDecimal("0.95"))
                                )
                        ),
                        1 // CHF only
                )
        );
    }

    @ParameterizedTest
    @MethodSource("getSeriesByRange_simpleCases")
    void getSeriesByRange_returnsSeries_andCallsClient(
            LocalDate start,
            LocalDate end,
            Map<LocalDate, Map<String, Optional<BigDecimal>>> clientResponse,
            int expectedSeriesCount
    ) {
        // given
        when(client.fetchEurFxSeriesByRange(start, end)).thenReturn(clientResponse);

        // when
        List<FxRateSeriesResponse> result = service.getSeriesByRange(start, end);

        // then
        assertNotNull(result);
        assertEquals(expectedSeriesCount, result.size());

        FxRateSeriesResponse series = result.get(0);
        assertEquals(FxRateService.EUR, series.baseCurrency());
        assertEquals(start, series.start());
        assertEquals(end, series.end());
        assertTrue(series.count() >= 1);
        assertFalse(series.rates().isEmpty());

        verify(client).fetchEurFxSeriesByRange(start, end);
    }

}
