package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.error.RateNotFoundException;
import com.crewmeister.cmcodingchallenge.error.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class BundesbankFxClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundesbankFxClient.class);
    public static final String DETAIL_PARAM = "detail";
    public static final String ID_PARAM = "id";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String GET_ALL_CURRENCY_PATH = "/data/BBEX3/D.%s.EUR.BB.AC.000";
    private static final String DETAIL_PARAM_VALUE = "serieskeyonly";
    private static final String SDMX_MEDIA_TYPE = "application/vnd.sdmx.data+json;version=1.0.0";
    private static final String CURRENCY_DIMENSION_ID = "BBK_STD_CURRENCY";


    public BundesbankFxClient(RestClient bundesbankRestClient, ObjectMapper objectMapper) {
        this.restClient = bundesbankRestClient;
        this.objectMapper = objectMapper;
    }

    public List<String> fetchCurrencies() {
        String path = GET_ALL_CURRENCY_PATH.formatted("");;
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam(DETAIL_PARAM, DETAIL_PARAM_VALUE)
                        .build())
                .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new UpstreamServiceException("Bundesbank response was empty");
        }

        return extractCurrenciesFromSdmx(response);
    }

    public BigDecimal fetchEurFxRate(String currency, LocalDate date) {
        String path = GET_ALL_CURRENCY_PATH.formatted(currency);

        try {
            LOGGER.info("Fetching EUR-FX rate from Bundesbank (currency={}, date={})", currency, date);

            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("startPeriod", date)
                            .queryParam("endPeriod", date)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new UpstreamServiceException("Bundesbank response was empty");
            }

            BigDecimal rate = extractSingleObservationRate(json, currency, date);
            LOGGER.info("Bundesbank EUR-FX rate fetched (currency={}, date={}, rate={})", currency, date, rate);
            return rate;

        } catch (RestClientResponseException ex) {
            LOGGER.error("Bundesbank HTTP error (status={}, currency={}, date={})",
                    ex.getRawStatusCode(), currency, date, ex);
            throw new UpstreamServiceException("Bundesbank API returned HTTP " + ex.getRawStatusCode(), ex);

        } catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error (currency={}, date={})", currency, date, ex);
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
        }
    }


    private List<String> extractCurrenciesFromSdmx(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);


            JsonNode seriesDims = root.path("data")
                    .path("structure")
                    .path("dimensions")
                    .path("series");

            if (!seriesDims.isArray()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dimensions.series is not an array");
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
            throw new UpstreamServiceException("Currency dimension BBK_STD_CURRENCY not found in SDMX response");

        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse SDMX currencies", e);
        }
    }

    private BigDecimal extractSingleObservationRate(String json, String currency, LocalDate date) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Observations are typically here:
            // data.dataSets[0].series["0:0:0:0:0:0"].observations["0"][0] = "1.0945"
            JsonNode seriesNode = root.path("data")
                    .path("dataSets").path(0)
                    .path("series");

            if (!seriesNode.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dataSets[0].series missing");
            }

            // Take first series (there should be exactly one for a specific currency)
            String firstSeriesKey = seriesNode.fieldNames().hasNext() ? seriesNode.fieldNames().next() : null;
            if (firstSeriesKey == null) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode observations = seriesNode.path(firstSeriesKey).path("observations");
            if (!observations.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: observations missing");
            }

            // Take first observation (there should be exactly one for single day)
            String firstObsKey = observations.fieldNames().hasNext() ? observations.fieldNames().next() : null;
            if (firstObsKey == null) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode obsArr = observations.path(firstObsKey);
            // obsArr looks like: ["1.0945", 1]
            String rateStr = obsArr.path(0).asText(null);
            if (rateStr == null || rateStr.isBlank()) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            return new BigDecimal(rateStr);

        } catch (RateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse SDMX rate response", e);
        }
    }
}


