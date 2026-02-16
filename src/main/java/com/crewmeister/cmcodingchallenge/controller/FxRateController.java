package com.crewmeister.cmcodingchallenge.controller;


import com.crewmeister.cmcodingchallenge.dto.*;
import com.crewmeister.cmcodingchallenge.error.InvalidRequestException;
import com.crewmeister.cmcodingchallenge.service.ConversionService;
import com.crewmeister.cmcodingchallenge.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PastOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController()
@RequestMapping("/api/v1/fx")
@Validated
@Tag(name = "FX Rate API", description = "EUR-based foreign exchange rate endpoints using Bundesbank data")
public class FxRateController {

    private final FxRateService fxRateService;
    private final ConversionService conversionService;
    private static final int DEFAULT_LAST_N = 10;
    private static final int MAX_YEARS_RANGE = 1;

    public FxRateController(FxRateService fxRateService, ConversionService conversionService) {
        this.fxRateService = fxRateService;
        this.conversionService = conversionService;
    }

    @GetMapping("/rates/{date}")
    @Operation(summary = "Get EUR-FX exchange rate for a specific day",
            description = """
            Returns all EUR-based rates available on a specific date.  
                
            **Behavior:**
            - If no observations exist for the given date, response includes a **failures.NO_DATA** message
            - Date must be in the past or present (future dates not allowed)
            - Rates are expressed as: 1 EUR = X foreign currency
            """)
    public FxRateResponse getRate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent
                                  LocalDate date) {
        return fxRateService.getAllEurFxRatePerDate(date);
    }

    @GetMapping("/rates")
    @Operation( summary = "Get EUR-FX exchange rates as a time-series collection",
            description = """
            Fetches EUR-based exchange-rate series for all available currencies over a date range.
            
            **Behavior:**
            - If **start** and **end** are not provided, the service defaults to the **last N available observations** (configurable, default: 10)
            - Date ranges are validated against a configurable maximum range (currently 1 year) to prevent excessive data load
            - Both parameters must be provided together or omitted together
            """)
    public FxRateSeriesCollectionResponse getRates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {

        if ((start == null) != (end == null)) {
            throw new InvalidRequestException("Provide both start and end, or neither");
        }

        if (start != null) {
            validateRange(start, end);
            return fxRateService.getSeriesByRange(start, end);
        }
        return fxRateService.getLastNSeries(DEFAULT_LAST_N);
    }


    @GetMapping("/convert")
    @Operation(summary = "Get foreign exchange amount for a given currency converted to EUR on a specific day")
    public ConversionResponse convert(@Valid ConvertRequest request) {
        return conversionService.convertFxToEur(request.currency(), request.date(), request.amount());
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

