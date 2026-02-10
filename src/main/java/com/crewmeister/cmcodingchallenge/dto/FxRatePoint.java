package com.crewmeister.cmcodingchallenge.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRatePoint(LocalDate date, BigDecimal rate) {}

