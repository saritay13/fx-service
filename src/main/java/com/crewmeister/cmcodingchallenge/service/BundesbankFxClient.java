package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.dto.FxRatePoint;
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
import java.util.Iterator;
import java.util.List;

import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.*;

@Component
public class BundesbankFxClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundesbankFxClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;


    public BundesbankFxClient(RestClient bundesbankRestClient, ObjectMapper objectMapper) {
        this.restClient = bundesbankRestClient;
        this.objectMapper = objectMapper;
    }

    public List<String> fetchAllAvailableCurrencies() {
        String path = GET_ALL_CURRENCY_PATH.formatted("");;
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam(DETAIL_PARAM, SERIES_KEY_ONLY)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new UpstreamServiceException(BUNDESBANK_EMPTY_RESPONSE);
            }

            return extractCurrenciesFromSdmx(response);
        } catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error");
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
        }
    }

    public BigDecimal fetchEurFxRateOnParticularDay(String currency, LocalDate date) {
        String path = GET_ALL_CURRENCY_PATH.formatted(currency);

        try {
            LOGGER.info("Fetching EUR-FX rate from Bundesbank (currency={}, date={})", currency, date);

            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam(START_PERIOD, date)
                            .queryParam(END_PERIOD, date)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new UpstreamServiceException(BUNDESBANK_EMPTY_RESPONSE);
            }

            BigDecimal rate = extractCurrencyRateFromSdmx(json, currency, date);
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

    public List<FxRatePoint> fetchEurFxSeriesByRange(String currency, LocalDate start, LocalDate end) {
        String path = GET_ALL_CURRENCY_PATH.formatted(currency);
        try {
            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam(START_PERIOD, start)
                            .queryParam(END_PERIOD, end)
                            .queryParam(DETAIL_PARAM, DATA_ONLY)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                LOGGER.error("Bundesbank empty JSON");
                throw new UpstreamServiceException(BUNDESBANK_EMPTY_RESPONSE);
            }

            return extractFxRatePoints(json);
        }catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error (currency={}, start={}, end={} )", currency, start,end,  ex);
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
        }

    }


    public List<FxRatePoint> fetchEurFxSeriesLastN(String currency, int lastN) {
        String path = GET_ALL_CURRENCY_PATH.formatted(currency);
        try {
            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam(LAST_N_OBSERVATIONS, lastN)
                            .queryParam(DETAIL_PARAM, DATA_ONLY)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                LOGGER.error("Bundesbank empty JSON");
                throw new UpstreamServiceException("Bundesbank response was empty");
            }

            return extractFxRatePoints(json);
        }catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error (currency={},lastN= {} )", currency, lastN,  ex);
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
        }
    }

    private List<FxRatePoint> extractFxRatePoints(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode timeValues = root.path(DATA_TAG)
                    .path(STRUCTURE_TAG)
                    .path(DIMENSIONS_TAG)
                    .path(OBSERVATION_TAG)
                    .path(0)
                    .path(VALUES_TAG);

            List<LocalDate> dates = new ArrayList<>();
            if (timeValues.isArray()) {
                for (JsonNode tv : timeValues) {
                    String d = tv.path(ID_TAG).asText(null);
                    if (d != null) {
                        dates.add(LocalDate.parse(d));
                    }
                }
            }
            JsonNode seriesNode = root.path(DATA_TAG)
                    .path(DATA_SETS_TAG).path(0)
                    .path(SERIES_TAG);

            String firstSeriesKey = seriesNode.fieldNames().hasNext() ? seriesNode.fieldNames().next() : null;
            if (firstSeriesKey == null) {
                return List.of();
            }

            JsonNode observations = seriesNode.path(firstSeriesKey).path("observations");
            if (!observations.isObject()) {
                return List.of();
            }

            List<FxRatePoint> fxRatePoints = new ArrayList<>();
            Iterator<String> observationKeys = observations.fieldNames();
            while (observationKeys.hasNext()) {
                String idxStr = observationKeys.next();
                int idx = Integer.parseInt(idxStr);

                String rateStr = observations.path(idxStr).path(0).asText(null);
                if (rateStr == null || idx >= dates.size()) {
                    continue;
                }

                fxRatePoints.add(new FxRatePoint(dates.get(idx), new BigDecimal(rateStr)));
            }

            fxRatePoints.sort(Comparator.comparing(FxRatePoint::date));
            return List.copyOf(fxRatePoints);

        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse SDMX series response", e);
        }
    }


    private List<String> extractCurrenciesFromSdmx(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);


            JsonNode seriesDimensions = root.path(DATA_TAG)
                    .path(STRUCTURE_TAG)
                    .path(DIMENSIONS_TAG)
                    .path(SERIES_TAG);

            if (!seriesDimensions.isArray()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dimensions.series is not an array");
            }

            for (JsonNode dimension : seriesDimensions) {
                if (CURRENCY_DIMENSION_ID.equals(dimension.path(ID_TAG).asText())) {
                    JsonNode values = dimension.path(VALUES_TAG);
                    if (!values.isArray()) {
                        return List.of();
                    }

                    List<String> currencies = new ArrayList<>();
                    for (JsonNode v : values) {
                        String currencyCode = v.path(ID_TAG).asText(null);
                        if (currencyCode != null && !currencyCode.isBlank()) {
                            currencies.add(currencyCode);
                        }
                    }
                    currencies.sort(Comparator.naturalOrder());
                    return currencies;
                }
            }
            throw new UpstreamServiceException("Currency dimension BBK_STD_CURRENCY not found in SDMX response");

        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse SDMX currencies", e);
        }
    }

    private BigDecimal extractCurrencyRateFromSdmx(String json, String currency, LocalDate date) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode seriesNode = root.path(DATA_TAG)
                    .path(DATA_SETS_TAG).path(0)
                    .path(SERIES_TAG);

            if (!seriesNode.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dataSets[0].series missing");
            }

            String firstSeriesKey = seriesNode.fieldNames().hasNext() ? seriesNode.fieldNames().next() : null;
            if (firstSeriesKey == null) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode observations = seriesNode.path(firstSeriesKey).path("observations");
            if (!observations.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: observations missing");
            }


            String firstObservationKey = observations.fieldNames().hasNext() ? observations.fieldNames().next() : null;
            if (firstObservationKey == null) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode observationList = observations.path(firstObservationKey);

            String rateAsString = observationList.path(0).asText(null);
            if (rateAsString == null || rateAsString.isBlank()) {
                throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            return new BigDecimal(rateAsString);

        } catch (RateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Failed to parse SDMX rate response", e);
        }
    }
}


