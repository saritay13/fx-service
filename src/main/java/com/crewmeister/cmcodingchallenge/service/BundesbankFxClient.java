package com.crewmeister.cmcodingchallenge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class BundesbankFxClient {

    public static final String DETAIL_PARAM = "detail";
    public static final String ID_PARAM = "id";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String GET_ALL_CURRENCY_PATH = "/data/BBEX3/D..EUR.BB.AC.000";
    private static final String DETAIL_PARAM_VALUE = "serieskeyonly";
    private static final String SDMX_MEDIA_TYPE = "application/vnd.sdmx.data+json;version=1.0.0";
    private static final String CURRENCY_DIMENSION_ID = "BBK_STD_CURRENCY";

    public BundesbankFxClient(RestClient bundesbankRestClient, ObjectMapper objectMapper) {
        this.restClient = bundesbankRestClient;
        this.objectMapper = objectMapper;
    }

    public List<String> fetchCurrencies() {
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GET_ALL_CURRENCY_PATH)
                        .queryParam(DETAIL_PARAM, DETAIL_PARAM_VALUE)
                        .build())
                .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Bundesbank response was empty");
        }

        return extractCurrenciesFromSdmx(response);
    }

    private List<String> extractCurrenciesFromSdmx(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);


            JsonNode seriesDims = root.path("data")
                    .path("structure")
                    .path("dimensions")
                    .path("series");

            if (!seriesDims.isArray()) {
                throw new IllegalStateException("Unexpected SDMX structure: dimensions.series is not an array");
            }

            // Find the currency dimension (id = BBK_STD_CURRENCY)
            for (JsonNode dim : seriesDims) {
                if (CURRENCY_DIMENSION_ID.equals(dim.path(ID_PARAM).asText())) {
                    JsonNode values = dim.path("values");
                    if (!values.isArray()) {
                        return List.of();
                    }

                    List<String> currencies = new ArrayList<>();
                    for (JsonNode v : values) {
                        String code = v.path(ID_PARAM).asText(null);
                        if (code != null && !code.isBlank()) {
                            currencies.add(code);
                        }
                    }

                    // Optional: sort for stable output
                    currencies.sort(Comparator.naturalOrder());
                    return currencies;
                }
            }

            // If not found, return empty or throw (I’d throw to detect provider schema changes)
            throw new IllegalStateException("Currency dimension BBK_STD_CURRENCY not found in SDMX response");

        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse SDMX currencies", e);
        }
    }
}

