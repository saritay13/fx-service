package com.crewmeister.cmcodingchallenge.controller;

import com.crewmeister.cmcodingchallenge.domain.FxRate;
import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import com.crewmeister.cmcodingchallenge.dto.ConvertRequest;
import com.crewmeister.cmcodingchallenge.dto.FxRateResponse;
import com.crewmeister.cmcodingchallenge.service.ConversionService;
import com.crewmeister.cmcodingchallenge.service.CurrencyService;
import com.crewmeister.cmcodingchallenge.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController()
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currency", description = "Currency and FX rate endpoints")
public class CurrencyController {

    private final CurrencyService currencyService;
    private final FxRateService fxRateService;
    private final ConversionService conversionService;

    public CurrencyController(CurrencyService currencyService, FxRateService fxRateService, ConversionService conversionService) {
        this.currencyService = currencyService;
        this.fxRateService = fxRateService;
        this.conversionService = conversionService;
    }
    @GetMapping
    @Operation(summary = "Get all available currencies")
    public List<String> getCurrencies() {
        return currencyService.getCurrencies();
    }

    @GetMapping("/rates/{currency}/{date}")
    @Operation(summary = "Get EUR exchange rate for a currency on a specific date")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rate found"),
            @ApiResponse(responseCode = "404", description = "Rate not available"),
            @ApiResponse(responseCode = "503", description = "Upstream service unavailable")
    })
    public FxRateResponse getRate(@PathVariable
                                      @NotBlank
                                      @Pattern(regexp = "^[A-Z]{3}$")
                                      String currency,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                  @PastOrPresent
                                  @PathVariable LocalDate date) {
        FxRate rate = fxRateService.getRate(currency, date);

        return new FxRateResponse(
                rate.date(),
                rate.baseCurrency(),
                rate.currency(),
                rate.rate(),
                "1 EUR = " + rate.rate() + " " + rate.currency()
        );
    }

    @GetMapping("/convert")
    public ConversionResponse convert(@Valid ConvertRequest request) {
        return conversionService.convertFxToEur(request.currency(), request.date(), request.amount());
    }
}
