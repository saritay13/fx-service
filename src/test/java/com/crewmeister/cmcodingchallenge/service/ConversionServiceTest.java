package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import com.crewmeister.cmcodingchallenge.dto.FxRate;
import com.crewmeister.cmcodingchallenge.error.RateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    @Mock
    private FxRateService fxRateService;

    private ConversionService service;

    @BeforeEach
    void setUp() {
        service = new ConversionService(fxRateService);
    }

    static Stream<Arguments> convertFxToEur_cases() {
        return Stream.of(
                Arguments.of("USD", LocalDate.of(2024, 1, 10),
                        new BigDecimal("110.00"),
                        new BigDecimal("1.10"),
                        new BigDecimal("100.00")),

                Arguments.of("GBP", LocalDate.of(2024, 2, 5),
                        new BigDecimal("86.00"),
                        new BigDecimal("0.86"),
                        new BigDecimal("100.00"))
        );
    }

    @ParameterizedTest
    @MethodSource("convertFxToEur_cases")
    void convertFxToEur_returnsConvertedAmount(
            String currency,
            LocalDate date,
            BigDecimal amountFx,
            BigDecimal rate,
            BigDecimal expectedEur
    ) {

        FxRate fxRate = mock(FxRate.class);
        when(fxRate.rate()).thenReturn(rate);
        when(fxRateService.getEurFxRatePerDate(currency, date)).thenReturn(fxRate);

        ConversionResponse response =
                service.convertFxToEur(currency, date, amountFx);

        assertNotNull(response);
        assertEquals("EUR", response.baseCurrency());
        assertEquals(currency, response.currency());
        assertEquals(date, response.date());
        assertEquals(amountFx, response.originalAmount());
        assertEquals(rate, response.rate());
        assertEquals(expectedEur, response.convertedAmountEur());

        verify(fxRateService).getEurFxRatePerDate(currency, date);
    }

    @ParameterizedTest
    @MethodSource("convert_missingRate_cases")
    void convertFxToEur_whenRateMissing_propagatesRateNotFound(String currency, LocalDate date, BigDecimal amountFx) {
        when(fxRateService.getEurFxRatePerDate(currency, date))
                .thenThrow(new RateNotFoundException("Rate not available for " + currency + " on " + date));

        assertThrows(RateNotFoundException.class, () -> service.convertFxToEur(currency, date, amountFx));

        verify(fxRateService).getEurFxRatePerDate(currency, date);
    }

    static Stream<Arguments> convert_missingRate_cases() {
        return Stream.of(
                Arguments.of("USD", LocalDate.of(2024, 1, 11), new BigDecimal("100")),
                Arguments.of("GBP", LocalDate.of(2024, 2, 6), new BigDecimal("50"))
        );
    }
}

