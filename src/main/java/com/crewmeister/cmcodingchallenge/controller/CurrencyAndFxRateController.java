package com.crewmeister.cmcodingchallenge.controller;

import com.crewmeister.cmcodingchallenge.domain.FxRate;
import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import com.crewmeister.cmcodingchallenge.dto.ConvertRequest;
import com.crewmeister.cmcodingchallenge.dto.FxRateResponse;
import com.crewmeister.cmcodingchallenge.dto.FxRateSeriesResponse;
import com.crewmeister.cmcodingchallenge.error.InvalidRequestException;
import com.crewmeister.cmcodingchallenge.service.ConversionService;
import com.crewmeister.cmcodingchallenge.service.CurrencyService;
import com.crewmeister.cmcodingchallenge.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController()
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Currency and FX rate endpoints")
public class CurrencyAndFxRateController {

    private final CurrencyService currencyService;
    private final FxRateService fxRateService;
    private final ConversionService conversionService;
    private static final int DEFAULT_LAST_N = 365;
    private static final int MAX_YEARS_RANGE = 5;

    public CurrencyAndFxRateController(CurrencyService currencyService, FxRateService fxRateService, ConversionService conversionService) {
        this.currencyService = currencyService;
        this.fxRateService = fxRateService;
        this.conversionService = conversionService;
    }
    @GetMapping("/currencies")
    @Operation(summary = "Get all available currencies")
    public List<String> getCurrencies() {
        return currencyService.getAllAvailableCurrencies();
    }

    @GetMapping("/rates/{currency}/{date}")
    @Operation(summary = "Get EUR exchange rate for a currency on a specific day")
    public FxRateResponse getRate(@PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent
                                  @PathVariable LocalDate date) {
        FxRate rate = fxRateService.getEurFxRate(currency, date);

        return new FxRateResponse(
                rate.date(),
                rate.baseCurrency(),
                rate.currency(),
                rate.rate(),
                "1 EUR = " + rate.rate() + " " + rate.currency()
        );
    }

    @GetMapping("/convert")
    @Operation(summary = "Get foreign exchange amount for a given currency converted to EUR on a specific day")
    public ConversionResponse convert(@Valid ConvertRequest request) {
        return conversionService.convertFxToEur(request.currency(), request.date(), request.amount());
    }

    @GetMapping("/rates")
    @Operation(summary = "Get all EUR exchange rates at all available dates as a collection")
    public FxRateSeriesResponse getRates(
            @RequestParam @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {

        if ((start == null) != (end == null)) {
            throw new InvalidRequestException("Provide both start and end, or neither");
        }

        if (start != null) {
            validateRange(start, end);
            return fxRateService.getSeriesByRange(currency, start, end);
        }
        return fxRateService.getLastNSeries(currency, DEFAULT_LAST_N);
    }

    private void validateRange(LocalDate start, LocalDate end) throws InvalidRequestException {
        if (end.isBefore(start)) {
            throw new InvalidRequestException("end must be on/after start");
        }
        if (start.plusYears(MAX_YEARS_RANGE).isBefore(end)) {
            throw new InvalidRequestException("Date range too large. Max allowed range is " + MAX_YEARS_RANGE + " years.");
        }
    }

}
