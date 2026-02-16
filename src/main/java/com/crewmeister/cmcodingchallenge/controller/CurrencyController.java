package com.crewmeister.cmcodingchallenge.controller;

import com.crewmeister.cmcodingchallenge.dto.CurrencyListResponse;
import com.crewmeister.cmcodingchallenge.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("/api/v1/currencies")
@Validated
@Tag(name = "Currency endpoints")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }
    @GetMapping()
    @Operation(summary = "Get all available currencies",
            description = """
            Returns all available currencies as structured metadata (`code`, `name`) plus a `count`.
            """)
    public CurrencyListResponse getCurrencies() {
        return currencyService.getAllAvailableCurrencies();
    }

}
