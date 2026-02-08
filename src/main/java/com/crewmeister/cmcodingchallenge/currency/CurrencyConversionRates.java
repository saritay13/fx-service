package com.crewmeister.cmcodingchallenge.currency;

import java.math.BigDecimal;

public class CurrencyConversionRates {
    private BigDecimal conversionRate;

    public CurrencyConversionRates(BigDecimal conversionRate) {
        this.conversionRate = conversionRate;
    }

    public BigDecimal getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(BigDecimal conversionRate) {
        this.conversionRate = conversionRate;
    }
}
