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
import java.util.List;

@RestController()
@RequestMapping("/api/v1/fx")
@Validated
@Tag(name = "FX rate endpoints")
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
    @Operation(summary = "Get EUR exchange rate for a currency on a specific day")
    public FxRateResponse getRate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent
                                  LocalDate date) {
        return fxRateService.getAllEurFxRatePerDate(date);
    }

    @GetMapping("/rates")
    @Operation(summary = "Get all EUR exchange rates at all available dates as a collection")
    public List<FxRateSeriesResponse> getRates(
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

