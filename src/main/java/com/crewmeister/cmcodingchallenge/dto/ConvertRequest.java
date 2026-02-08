package com.crewmeister.cmcodingchallenge.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConvertRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 18, fraction = 6)
        BigDecimal amount,

        @NotNull
        @PastOrPresent
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date
) {}
