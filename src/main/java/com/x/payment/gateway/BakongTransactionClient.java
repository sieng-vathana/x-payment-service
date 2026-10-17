package com.x.payment.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.payment.config.BakongApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Asks the Bakong Open API whether a KHQR, identified by its MD5, has been paid. */
@Component
public class BakongTransactionClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    /** Bakong answers responseCode 1 with errorCode 1 when no transaction exists for the hash yet. */
    private static final int TRANSACTION_NOT_FOUND = 1;

    private final BakongApiProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public BakongTransactionClient(BakongApiProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper());
    }

    BakongTransactionClient(BakongApiProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** Returns the transaction once the KHQR is paid, or empty while it is still unpaid. */
    public Optional<BakongTransaction> findByMd5(String md5) {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new BakongApiException("Automatic Bakong check is off: no API token is set.");
        }
        URI endpoint = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/v1/check_transaction_by_md5")
                .build()
                .toUri();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.getToken().trim())
                .POST(HttpRequest.BodyPublishers.ofString(json(Map.of("md5", md5))))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new BakongApiException("Bakong could not be reached: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BakongApiException("Bakong check was interrupted.");
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new BakongApiException("Bakong refused the request (HTTP " + status
                    + "). Check that the API token is valid and that this server may call the Bakong API.");
        }
        if (status < 200 || status >= 300) {
            throw new BakongApiException("Bakong check failed (HTTP " + status + ").");
        }

        JsonNode body = readJson(response.body());
        JsonNode data = body.path("data");
        int responseCode = body.path("responseCode").asInt(-1);
        if (responseCode == 0 && data.isObject()) {
            return Optional.of(new BakongTransaction(
                    text(data, "hash"), decimal(data, "amount"), text(data, "currency"),
                    text(data, "fromAccountId"), text(data, "toAccountId")));
        }
        if (responseCode == 1 && body.path("errorCode").asInt(-1) == TRANSACTION_NOT_FOUND) {
            return Optional.empty();
        }
        throw new BakongApiException("Bakong check failed: "
                + body.path("responseMessage").asText("unexpected response"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Bakong request could not be serialized", exception);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "" : body);
        } catch (JsonProcessingException exception) {
            throw new BakongApiException("Bakong returned a response that is not JSON.");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** A Bakong transaction found for a KHQR hash. Fields Bakong leaves out are null. */
    public record BakongTransaction(
            String hash, BigDecimal amount, String currency, String fromAccountId, String toAccountId) {

        /** True when this transaction pays the expected amount in the expected currency. */
        public boolean settles(BigDecimal expectedAmount, String expectedCurrency) {
            if (currency != null && !currency.trim().equalsIgnoreCase(expectedCurrency)) {
                return false;
            }
            if (amount == null) {
                return true;
            }
            int scale = "KHR".equalsIgnoreCase(expectedCurrency) ? 0 : 2;
            return amount.setScale(scale, RoundingMode.HALF_UP)
                    .compareTo(expectedAmount.setScale(scale, RoundingMode.HALF_UP)) == 0;
        }
    }

    /** The Bakong API could not answer; the payment stays pending and can still be confirmed by hand. */
    public static class BakongApiException extends RuntimeException {
        public BakongApiException(String message) {
            super(message);
        }
    }
}
