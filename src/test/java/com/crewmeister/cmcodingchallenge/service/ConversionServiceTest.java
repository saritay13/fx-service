package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import com.crewmeister.cmcodingchallenge.dto.FxRate;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        // given
        FxRate fxRate = mock(FxRate.class);
        when(fxRate.rate()).thenReturn(rate);
        when(fxRateService.getEurFxRatePerDate(currency, date)).thenReturn(fxRate);

        // when
        ConversionResponse response =
                service.convertFxToEur(currency, date, amountFx);

        // then
        assertNotNull(response);
        assertEquals("EUR", response.baseCurrency());
        assertEquals(currency, response.currency());
        assertEquals(date, response.date());
        assertEquals(amountFx, response.originalAmount());
        assertEquals(rate, response.rate());
        assertEquals(expectedEur, response.convertedAmountEur());

        verify(fxRateService).getEurFxRatePerDate(currency, date);
    }
}

