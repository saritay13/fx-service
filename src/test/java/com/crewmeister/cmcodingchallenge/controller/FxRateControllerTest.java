package com.crewmeister.cmcodingchallenge.controller;


import com.crewmeister.cmcodingchallenge.dto.ConversionResponse;
import com.crewmeister.cmcodingchallenge.service.ConversionService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ExtendWith(MockitoExtension.class)
public class FxRateControllerTest {

    public static final String MOCK_SDMX_JSON_PATH = "src/test/resources/wiremock/";
    private static final String CCY = "USD";
    private static final String API_BASE = "/api/v1/fx";
    public static final String AMOUNT_100 = "100";
    public static final String $_STATUS = "$.status";
    public static final String $_MESSAGE = "$.message";
    public static final int BAD_REQUEST_400 = 400;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ConversionService conversionService;

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
    void getRate_returns200ForValidRequest() throws Exception {
        String date = "2024-01-15";
        String rate = "1.0945";
        String json = getSingleDaySdmxJson(date, rate);


        stubRateResponse(date, json);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/"  + date))
                .andExpect(status().isOk());
    }



    @Test
    void getRate_returns503ForUpstreamFailure() throws Exception {
        String date = "2024-01-13";
        getWireMockWith500Status(date);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/"  + date))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }


    @Test
    void getRate_returns400ForInvalidDateFormat() throws Exception {
        String invalidDate = "15-02-2024";
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + invalidDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.BAD_REQUEST.value()));
    }


    @ParameterizedTest(name = "Invalid Dates ''{0}'' should return 400")
    @ValueSource(strings = { "01-02-2024", "", "2027-01-01"})
    void convert_returns400ForInvalidDates(String date) throws Exception {
        mockMvcPerformConvert(date, CCY,AMOUNT_100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.BAD_REQUEST.value()))
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

    @Test
    void convert_returns200ForValidRequest() throws Exception {
        String date = "2024-01-15";
        String amount = "100";

        ConversionResponse mocked = new ConversionResponse(
                LocalDate.parse(date),
                "EUR",
                CCY,
                new BigDecimal(amount),
                new BigDecimal("1.10"),
                new BigDecimal("90.91")
        );

        when(conversionService.convertFxToEur(eq(CCY), eq(LocalDate.parse(date)), eq(new BigDecimal(amount))))
                .thenReturn(mocked);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/convert")
                        .queryParam("currency", CCY)
                        .queryParam("date", date)
                        .queryParam("amount", amount))
                .andExpect(status().isOk());
    }

    @Test
    void convert_whenRateMissing_returns404WithHelpfulMessage() throws Exception {
        String date = "2024-01-16";

        when(conversionService.convertFxToEur(eq(CCY), eq(LocalDate.parse(date)), eq(new BigDecimal(AMOUNT_100))))
                .thenThrow(new com.crewmeister.cmcodingchallenge.error.RateNotFoundException("Rate not available for " + CCY + " on " + date));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/convert")
                        .queryParam("currency", CCY)
                        .queryParam("date", date)
                        .queryParam("amount", AMOUNT_100))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath($_MESSAGE).value("Rate not available for " + CCY + " on " + date));
    }

    @Test
    void getRate_whenNoDataReturned_includesFailureMessage() throws Exception {
        String date = "2024-01-14";
        String json = getNoObservationSdmxJson(date);

        stubRateResponse(date, json);

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates/" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCurrencies").value(0))
                .andExpect(jsonPath("$.successfulRates", hasSize(0)))
                .andExpect(jsonPath("$.failures").isNotEmpty());
    }


    @Test
    void getRates_withStartEnd_returns200() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/wiremock/series-range.json"))
                .replace("{{d0}}", "2024-01-15")
                .replace("{{d1}}", "2024-01-16")
                .replace("{{usd_r0}}", "1.0950")
                .replace("{{usd_r1}}", "1.0952")
                .replace("{{gbp_r0}}", "1.0952")
                .replace("{{gbp_r1}}", "1.0952");;

        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam("startPeriod", equalTo("2024-01-15"))
                .withQueryParam("endPeriod", equalTo("2024-01-16"))
                .withQueryParam("detail", equalTo("dataonly"))
                .willReturn(okJson(json)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("start", "2024-01-15")
                        .queryParam("end", "2024-01-16"))
                .andExpect(status().isOk());
    }


    @Test
    void getRates_onlyStartProvided_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2024-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath($_MESSAGE).value("Provide both start and end, or neither"));
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
                .andExpect(jsonPath($_MESSAGE).value("end must be on/after start"));
    }

    @Test
    void getRates_rangeTooLarge_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "2010-01-01")
                        .queryParam("end", "2020-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRates_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD")
                        .queryParam("start", "15-01-2024")
                        .queryParam("end", "16-01-2024"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void getRates_upstreamFails_returns503() throws Exception {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(DETAIL_PARAM, equalTo(DATA_ONLY))
                .withQueryParam(START_PERIOD, matching(".*"))
                .withQueryParam(END_PERIOD, matching(".*"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/rates")
                        .queryParam("currency", "USD"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath($_STATUS).value(HttpStatus.SERVICE_UNAVAILABLE.value()));
    }


    private void stubRateResponse(String date, String responseBody) {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(START_PERIOD, equalTo(date))
                .withQueryParam(END_PERIOD, equalTo(date))
                .willReturn(okJson(responseBody)));
    }

    private static void getWireMockWith500Status(String date) {
        wiremock.stubFor(get(urlPathEqualTo("/rest" + GET_ALL_CURRENCY_PATH.formatted("")))
                .withQueryParam(START_PERIOD, equalTo(date))
                .withQueryParam(END_PERIOD, equalTo(date))
                .willReturn(aResponse().withStatus(500).withBody("Bundesbank down")));
    }

    private static String getSingleDaySdmxJson(String date, String rate) throws IOException {
        return Files.readString(Path.of(MOCK_SDMX_JSON_PATH + "single-day.json")).
                replace("{{date}}", date)
                .replace("{{usd_rate}}", rate)
                .replace("{{gbp_rate}}", rate);
    }

    private static String getNoObservationSdmxJson(String date) throws IOException {
        return Files.readString(Path.of(MOCK_SDMX_JSON_PATH + "/single-day-no-observation.json")).
                replace("{{date}}", date);
    }

    private ResultActions mockMvcPerformConvert(String date, String currency, String amount) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(API_BASE + "/convert")
                .queryParam("currency", currency)
                .queryParam("date", date)
                .queryParam("amount", amount));
    }

}
