package com.crewmeister.cmcodingchallenge.controller;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.DETAIL_PARAM;
import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.GET_ALL_CURRENCY_PATH;
import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.SERIES_KEY_ONLY;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CurrencyControllerTest {

    public static final String MOCK_SDMX_JSON_PATH = "src/test/resources/wiremock/";
    private static final String API_BASE = "/api/v1";
    public static final String $_STATUS = "$.status";

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

}
