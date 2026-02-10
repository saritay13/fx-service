package com.crewmeister.cmcodingchallenge.controller;


import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CurrencyAndFxRateControllerIntegrationTest {

    public static final String MOCK_SDMX_JSON_PATH = "src/test/resources/wiremock/";
    private static final String CCY = "USD";
    private static final String API_BASE = "/api/v1";
    public static final String AMOUNT_100 = "100";
    public static final String $_STATUS = "$.status";
    public static final String $_MESSAGE = "$.message";
    public static final int BAD_REQUEST_400 = 400;

    @Autowired
    MockMvc mockMvc;

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry){
        registry.add("bundesbank.base-url", ()-> wiremock.getRuntimeInfo().getHttpBaseUrl()+ "/rest");
    }

    @BeforeEach
    void resetWireMock() {
        wiremock.resetAll();
    }

    @Test
    void getCurrencies_returns503_whenUpstreamResponseUnparseable() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(DETAIL_PARAM, equalTo(SERIES_KEY_ONLY))
                .willReturn(okJson("{\"bad\":\"shape\"}")));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE+ "/currencies"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @Test
    void getCurrencies_returns503_whenUpstreamFails() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(DETAIL_PARAM, equalTo(SERIES_KEY_ONLY))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE+ "/currencies"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @Test
    void getCurrencies_usesCache_upstreamCalledOnce() throws Exception {
        String json = Files.readString(Path.of(MOCK_SDMX_JSON_PATH + "all-currencies.json"));

        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(DETAIL_PARAM, equalTo(SERIES_KEY_ONLY))
                .willReturn(okJson(json)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE+ "/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").isString());

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE+ "/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").isString());

        wiremock.verify(1,
                getRequestedFor(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                        .withQueryParam(DETAIL_PARAM, equalTo(SERIES_KEY_ONLY)));
    }

    @Test
    void getRate_returns200ForValidRequest() throws Exception {
        String date = "2024-01-15";
        String rate = "1.0945";
        String json = getSingleDaySdmxJson(date, rate);


        stubRateResponse(date, json);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + CCY + "/" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(1.0945));
    }

    @Test
    void getRate_returns404WhenRateNotAvailable() throws Exception {
        String date = "2024-01-14";
        String json = getNoObservationSdmxJson(date);

        stubRateResponse(date, json);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + CCY + "/" + date))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath($_MESSAGE)
                .value("Rate not available for USD on 2024-01-14"));;
    }

    @Test
    void getRate_returns503ForUpstreamFailure() throws Exception {
        String date = "2024-01-13";
        getWireMockWith500Status(CCY, date);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + CCY + "/" + date))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ParameterizedTest(name = "Invalid currency ''{0}'' should return 400")
    @ValueSource(strings = { "usd", "UsD", "US", "123", "EURO"})
    void getRate_returns400ForInvalidCurrency(String currency) throws Exception {

        String date = "2024-01-13";

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + currency + "/" + date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath($_MESSAGE, containsString("currency")));
    }

    @Test
    void getRate_returns400ForInvalidDateFormat() throws Exception {
        String invalidDate = "15-02-2024";
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + CCY + "/"  + invalidDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void convert_returns200ForValidRequest() throws Exception {
        String date = "2024-01-15";
        String rate = "1.0945";
        String json = getSingleDaySdmxJson(date, rate);


        stubRateResponse(date, json);

        BigDecimal expected = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(1.0945), 2, RoundingMode.HALF_UP);

        mockMvcPerformConvert(date, CCY, AMOUNT_100)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmountEur").value(expected.doubleValue()));
    }


    @Test
    void convert_returns404_whenRateNotAvailable() throws Exception {
        String date = "2023-01-14";
        String json = getNoObservationSdmxJson(date);

        stubRateResponse(date, json);

        mockMvcPerformConvert(date, CCY, AMOUNT_100)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath($_STATUS).value(404))
                .andExpect(jsonPath($_MESSAGE, containsString("Rate not available")));
    }

    @Test
    void convert_returns503_whenUpstreamDown() throws Exception {
        String date = "2024-01-15";

        getWireMockWith500Status(CCY, date);

        mockMvcPerformConvert(date, CCY, AMOUNT_100)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ParameterizedTest(name = "Invalid currency ''{0}'' should return 400")
    @ValueSource(strings = { "usd", "UsD", "US", "123", "EURO", "null"})
    void convert_returns400ForInvalidCurrency(String currency) throws Exception {
        String date = "2020-01-13";
        mockMvcPerformConvert(date, currency, AMOUNT_100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath($_MESSAGE, containsString("currency")));
    }

    @ParameterizedTest(name = "Invalid Dates ''{0}'' should return 400")
    @ValueSource(strings = { "01-02-2024", "", "2027-01-01"})
    void convert_returns400ForInvalidDates(String date) throws Exception {
        mockMvcPerformConvert(date, CCY,AMOUNT_100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath($_MESSAGE, containsString("date")));
    }

    @ParameterizedTest(name = "Invalid Amount ''{0}'' should return 400")
    @ValueSource(strings = { "0", "-1", "null", "1000000000000000000", "abc", "1.1234567"})
    void convert_returns400ForInvalidAmounts(String amount) throws Exception {
        String date = "2020-01-13";
        mockMvcPerformConvert(date, CCY,amount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath($_STATUS).value(BAD_REQUEST_400))
                .andExpect(jsonPath($_MESSAGE, containsString("amount")));
    }


    private ResultActions mockMvcPerformConvert(String date, String currency, String amount) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/convert")
                .queryParam("currency", currency)
                .queryParam("date", date)
                .queryParam("amount", amount));
    }


    private void stubRateResponse(String date, String responseBody) {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted(CCY)))
                .withQueryParam(START_PERIOD, equalTo(date))
                .withQueryParam(END_PERIOD, equalTo(date))
                .willReturn(okJson(responseBody)));
    }

    private static void getWireMockWith500Status(String ccy, String date) {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted(ccy)))
                .withQueryParam(START_PERIOD, equalTo(date))
                .withQueryParam(END_PERIOD, equalTo(date))
                .willReturn(aResponse().withStatus(500).withBody("Bundesbank down")));
    }

    private static String getSingleDaySdmxJson(String date, String rate) throws IOException {
        return Files.readString(Path.of(MOCK_SDMX_JSON_PATH + "single-day.json")).
                replace("{{date}}", date)
                .replace("{{rate}}", rate);
    }

    private static String getNoObservationSdmxJson(String date) throws IOException {
        return Files.readString(Path.of(MOCK_SDMX_JSON_PATH + "/single-day-no-observation.json")).
                replace("{{date}}", date);
    }

    @Test
    void getRates_withStartEnd_returns200() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/wiremock/series-range.json"))
                .replace("{{d0}}", "2024-01-15")
                .replace("{{r0}}", "1.0945")
                .replace("{{d1}}", "2024-01-16")
                .replace("{{r1}}", "1.0950");

        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted(CCY)))
                .withQueryParam("startPeriod", equalTo("2024-01-15"))
                .withQueryParam("endPeriod", equalTo("2024-01-16"))
                .withQueryParam("detail", equalTo("dataonly"))
                .willReturn(okJson(json)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2024-01-15")
                        .queryParam("end", "2024-01-16"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.currency").value(CCY))
                .andExpect(jsonPath("$.rates").isArray());
    }

    @Test
    void getRates_withoutStartEnd_defaultsToLastN_returns200() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/wiremock/series-lastn.json"));

        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted(CCY)))
                .withQueryParam(LAST_N_OBSERVATIONS, equalTo("365"))
                .withQueryParam(DETAIL_PARAM, equalTo(DATA_ONLY))
                .willReturn(okJson(json)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.currency").value(CCY))
                .andExpect(jsonPath("$.rates").isArray())
                .andExpect(jsonPath("$.rates", hasSize(3)));;

    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US", "EURO", "U$D"})
    void getRates_invalidCurrency_returns400(String currency) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", currency))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));;
    }

    @Test
    void getRates_onlyStartProvided_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2024-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("Provide both start and end, or neither"));
    }

    @Test
    void getRates_onlyEndProvided_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("end", "2024-01-15"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRates_endBeforeStart_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2024-01-16")
                        .queryParam("end", "2024-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("end must be on/after start"));
    }

    @Test
    void getRates_rangeTooLarge_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2010-01-01")
                        .queryParam("end", "2020-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Date range too large. Max allowed range is 5 years."));
    }

    @Test
    void getRates_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "15-01-2024")
                        .queryParam("end", "16-01-2024"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void getRates_upstreamFails_returns503() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("USD")))
                .withQueryParam(LAST_N_OBSERVATIONS, equalTo("365"))
                .withQueryParam(DETAIL_PARAM, equalTo(DATA_ONLY))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

}
