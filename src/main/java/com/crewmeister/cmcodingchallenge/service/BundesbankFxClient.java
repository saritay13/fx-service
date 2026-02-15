package com.crewmeister.cmcodingchallenge.service;

import com.crewmeister.cmcodingchallenge.dto.FxRate;
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

import javax.swing.text.html.Option;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.crewmeister.cmcodingchallenge.constants.BundesbankClientConstants.*;
import static com.crewmeister.cmcodingchallenge.service.FxRateService.EUR;

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

    public Optional<BigDecimal> fetchEurFxRateOnParticularDay(String currency, LocalDate date) {
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

            Optional<BigDecimal> rate = extractCurrencyRateFromSdmx(json, currency, date);
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

    public Map<String, Optional<BigDecimal>> fetchAllEurFxRateOnParticularDay(LocalDate date) {
        String path = GET_ALL_CURRENCY_PATH.formatted("");

        try {
            LOGGER.info("Fetching EUR-FX rate from Bundesbank ( date={})",  date);

            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam(START_PERIOD, date)
                            .queryParam(END_PERIOD, date)
                            .queryParam(DETAIL_PARAM, DATA_ONLY)
                            .build())
                    .accept(MediaType.parseMediaType(SDMX_MEDIA_TYPE))
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new UpstreamServiceException(BUNDESBANK_EMPTY_RESPONSE);
            }

            Map<String, Optional<BigDecimal>> rates = extractAllCurrencyRatesFromSdmxOneDay(json, date);
            LOGGER.info("Bundesbank EUR-FX rate fetched (date={}, total rates={})",  date, rates.size());
            return rates;

        } catch (RestClientResponseException ex) {
            LOGGER.error("Bundesbank HTTP error (status={}, date={})",
                    ex.getRawStatusCode(), date, ex);
            throw new UpstreamServiceException("Bundesbank API returned HTTP " + ex.getRawStatusCode(), ex);

        } catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error (date={})", date, ex);
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
        }
    }

    public Map<LocalDate, Map<String, Optional<BigDecimal>>> fetchEurFxSeriesByRange(LocalDate start, LocalDate end) {
        String path = GET_ALL_CURRENCY_PATH.formatted("");
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

            return extractAllCurrencyRatesFromSdmxAllDay(json);
        }catch (RestClientException ex) {
            LOGGER.error("Bundesbank network/client error (currency={}, start={}, end={} )", start,end,  ex);
            throw new UpstreamServiceException("Failed to call Bundesbank API", ex);
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
            LOGGER.error(e.getMessage());
            throw new UpstreamServiceException("Failed to parse SDMX currencies", e);
        }
    }

    private Optional<BigDecimal> extractCurrencyRateFromSdmx(String json, String currency, LocalDate date) {
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
                return Optional.empty();
                //throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode observations = seriesNode.path(firstSeriesKey).path("observations");
            if (!observations.isObject()) {
                //return Optional.empty();
                throw new UpstreamServiceException("Unexpected SDMX structure: observations missing");
            }


            String firstObservationKey = observations.fieldNames().hasNext() ? observations.fieldNames().next() : null;
            if (firstObservationKey == null) {
                return  Optional.empty();
                //throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            JsonNode observationList = observations.path(firstObservationKey);

            String rateAsString = observationList.path(0).asText(null);
            if (rateAsString == null || rateAsString.isBlank()) {
                return Optional.empty();
                //throw new RateNotFoundException("Rate not available for " + currency + " on " + date);
            }

            return Optional.of(new BigDecimal(rateAsString));

        } catch (RateNotFoundException e) {
            return Optional.empty();
            //throw e;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            //return Optional.empty();
            throw new UpstreamServiceException("Failed to parse SDMX rate response for currency " + currency, e);
        }
    }

    private Map<String, Optional<BigDecimal>> extractAllCurrencyRatesFromSdmxOneDay(String json, LocalDate date) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 1) Extract dates (should be a single entry because start=end)
            List<LocalDate> dates = extractDates(root);
            if (dates.isEmpty() || !dates.get(0).equals(date)) {
                // If Bundesbank returns different/empty date list, treat as no data (not fatal)
                return Map.of();
            }

            List<String> currencyDimInfo = extractCurrencyDimInfo(root);
            if (currencyDimInfo.isEmpty()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: currency series dimension missing");
            }

            // 3) Read series object (contains many series keys for wildcard)
            JsonNode seriesNode = root.path("data")
                    .path("dataSets").path(0)
                    .path("series");

            if (!seriesNode.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dataSets[0].series missing");
            }

            // 4) Prepare a result map: currency -> Optional(rate)
            // We include every currency code we discovered, defaulting to Optional.empty().
            Map<String, Optional<BigDecimal>> result = new HashMap<>();
            currencyDimInfo.forEach(ccy -> result.put(ccy, Optional.empty()));

            // 5) Iterate each series entry and fill the result
            Iterator<String> seriesKeys = seriesNode.fieldNames();
            int idx = 0;
            while (seriesKeys.hasNext()) {
                String seriesKey = seriesKeys.next();

                String currency = resolveCurrencyFromSeriesKey(idx, currencyDimInfo);
                idx++;
                if (currency == null) continue;

                JsonNode observations = seriesNode.path(seriesKey).path("observations");
                if (!observations.isObject()) {
                    continue;
                }

                // One day => observation index "0"
                JsonNode obs0 = observations.path("0");
                String rateStr = obs0.path(0).asText(null);
                if (rateStr == null || rateStr.isBlank()) {
                    continue;
                }

                try {
                    result.put(currency, Optional.of(new BigDecimal(rateStr)));
                } catch (NumberFormatException ignored) {
                    // keep empty if the rate is not a valid decimal
                }
            }
            return result.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            java.util.LinkedHashMap::new
                    ));

        } catch (Exception e) {
            LOGGER.error("Failed to parse SDMX one-day wildcard response for date {}", date, e);
            throw new UpstreamServiceException("Failed to parse SDMX one-day response", e);
        }
    }

    private Map<LocalDate, Map<String, Optional<BigDecimal>>> extractAllCurrencyRatesFromSdmxAllDay(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            List<LocalDate> dates = extractDates(root);
            if (dates.isEmpty()) {
                return Map.of();
            }

            List<String> currencyDimInfo = extractCurrencyDimInfo(root);
            if (currencyDimInfo.isEmpty()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: currency series dimension missing");
            }

            // 3) Read series object (contains many series keys for wildcard)
            JsonNode seriesNode = root.path("data")
                    .path("dataSets").path(0)
                    .path("series");

            if (!seriesNode.isObject()) {
                throw new UpstreamServiceException("Unexpected SDMX structure: dataSets[0].series missing");
            }

            // 4) Prepare a result map: currency -> Optional(rate)
            // We include every currency code we discovered, defaulting to Optional.empty().
            Map<LocalDate, Map<String, Optional<BigDecimal>>> result = new LinkedHashMap<>();
            for (LocalDate d : dates) {
                Map<String, Optional<BigDecimal>> perDate = new LinkedHashMap<>();
                for (String ccy : currencyDimInfo) {
                    perDate.put(ccy, Optional.empty());
                }
                result.put(d, perDate);
            }

            // 5) Iterate each series entry and fill the result
            Iterator<String> seriesKeys = seriesNode.fieldNames();
            int idx = 0;
            int dateIdx=0;
            while (seriesKeys.hasNext()) {
                String seriesKey = seriesKeys.next();

                String currency = resolveCurrencyFromSeriesKey(idx, currencyDimInfo);
                idx++;
                if (currency == null) continue;

                JsonNode observations = seriesNode.path(seriesKey).path("observations");
                if (!observations.isObject()) {
                    continue;
                }

                Iterator<String> obsKeys = observations.fieldNames();
                while (obsKeys.hasNext()) {
                    String idxStr = obsKeys.next();

                    try {
                        dateIdx = Integer.parseInt(idxStr);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (dateIdx < 0 || dateIdx >= dates.size()) {
                        continue;
                    }

                    String rateStr = observations.path(idxStr).path(0).asText(null);
                    if (rateStr == null || rateStr.isBlank()) {
                        continue;
                    }

                    BigDecimal rate;
                    try {
                        rate = new BigDecimal(rateStr);
                    } catch (NumberFormatException ex) {
                        continue;
                    }

                    LocalDate date = dates.get(dateIdx);
                    result.get(date).put(currency, Optional.of(rate));
                }
            }

            return result;

        } catch (Exception e) {
            LOGGER.error("Failed to parse SDMX one-day wildcard response for date {}", e);
            throw new UpstreamServiceException("Failed to parse SDMX one-day response", e);
        }
    }

    private List<LocalDate> extractDates(JsonNode root) {
        JsonNode obsDims = root.path("data")
                .path("structure")
                .path("dimensions")
                .path("observation");

        if (!obsDims.isArray()) return List.of();

        for (JsonNode dim : obsDims) {
            if ("TIME_PERIOD".equals(dim.path("id").asText())) {
                JsonNode values = dim.path("values");
                if (!values.isArray()) return List.of();
                List<LocalDate> dates = new ArrayList<>();
                for (JsonNode v : values) {
                    String id = v.path("id").asText(null);
                    if (id != null) dates.add(LocalDate.parse(id));
                }
                return dates;
            }
        }
        return List.of();
    }

    private List<String> extractCurrencyDimInfo(JsonNode root) {
        JsonNode seriesDims = root.path("data")
                .path("structure")
                .path("dimensions")
                .path("series");

        if (!seriesDims.isArray()) return List.of();

        for (JsonNode dim : seriesDims) {
            if ("BBK_STD_CURRENCY".equals(dim.path("id").asText())) {
                int keyPos = dim.path("keyPosition").asInt(-1);
                JsonNode values = dim.path("values");
                List<String> codes = new ArrayList<>();
                if (values.isArray()) {
                    for (JsonNode v : values) {
                        String id = v.path("id").asText(null);
                        if (id != null) codes.add(id);
                    }
                }
                return codes;
            }
        }
        return  List.of();
    }

    private String resolveCurrencyFromSeriesKey(Integer idx, List<String> info) {
        if(idx < 0 || idx > info.size())
            return null;
        return info.get(idx);
    }
}


