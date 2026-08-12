package com.x.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.payment.config.KhqrPayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;

@Component
public class KhqrPayGateway implements QrPaymentGateway {
    private final KhqrPayProperties properties;
    private final KhqrPaySigner signer = new KhqrPaySigner();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KhqrPayGateway(KhqrPayProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper());
    }

    KhqrPayGateway(KhqrPayProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public QrPaymentInitiation initiate(String transactionId, BigDecimal amount, String items) {
        requireConfiguration();
        String merchantSecret = properties.getMerchantSecret().trim();
        String successUrl = absoluteCallbackUrl(properties.getSuccessUrl());
        String paymentRequestId = properties.getPaymentRequestId().trim();
        String encodedItems = encodeItemsWhenRequired(items);
        String hash = signer.sign(
                merchantSecret, transactionId, amount, successUrl, encodedItems);

        String endpoint = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment("api", "payment", "request", paymentRequestId)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        StringJoiner query = new StringJoiner("&");
        addQueryParameter(query, "transaction_id", transactionId);
        addQueryParameter(query, "amount", amount.toPlainString());
        addQueryParameter(query, "success_url", successUrl);
        addQueryParameter(query, "hash", hash);
        if (StringUtils.hasText(encodedItems)) {
            addQueryParameter(query, "items", encodedItems);
        }
        if (StringUtils.hasText(properties.getCancelUrl())) {
            addQueryParameter(query, "cancel_url", absoluteCallbackUrl(properties.getCancelUrl()));
        }

        String checkoutUrl = endpoint + "?" + query;
        verifyCheckoutAccepted(checkoutUrl);

        return new QrPaymentInitiation(
                transactionId,
                null,
                null,
                checkoutUrl,
                null);
    }

    private void verifyCheckoutAccepted(String checkoutUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(checkoutUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            KhqrPayResponse providerResponse = parseProviderResponse(response.body());
            if (providerResponse.responseCode() != null && providerResponse.responseCode() != 0) {
                throw rejectedCheckout(providerResponse.responseMessage());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                String message = StringUtils.hasText(providerResponse.responseMessage())
                        ? providerResponse.responseMessage()
                        : "HTTP " + response.statusCode();
                throw rejectedCheckout(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable(exception);
        } catch (IOException exception) {
            throw providerUnavailable(exception);
        }
    }

    private KhqrPayResponse parseProviderResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return new KhqrPayResponse(null, null);
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode responseCode = response.get("responseCode");
            JsonNode responseMessage = response.get("responseMessage");
            return new KhqrPayResponse(
                    responseCode == null || responseCode.isNull() ? null : responseCode.asInt(),
                    responseMessage == null || responseMessage.isNull() ? null : responseMessage.asText());
        } catch (IOException ignored) {
            return new KhqrPayResponse(null, null);
        }
    }

    private ResponseStatusException rejectedCheckout(String providerMessage) {
        String message = StringUtils.hasText(providerMessage)
                ? providerMessage
                : "Provider rejected the checkout request";
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "KHQRPay rejected checkout: " + message);
    }

    private ResponseStatusException providerUnavailable(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "KHQRPay is currently unavailable",
                cause);
    }

    private record KhqrPayResponse(Integer responseCode, String responseMessage) {
    }

    private String absoluteCallbackUrl(String configuredUrl) {
        String callbackUrl = configuredUrl.trim();
        URI callbackUri = URI.create(callbackUrl);
        if (callbackUri.isAbsolute()) {
            return callbackUrl;
        }

        String publicOrigin = properties.getPublicOrigin().trim();
        URI publicUri = URI.create(publicOrigin.endsWith("/") ? publicOrigin : publicOrigin + "/");
        if (!publicUri.isAbsolute()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KHQRPay public origin must be an absolute URL");
        }
        return publicUri.resolve(callbackUri).toString();
    }

    private void addQueryParameter(StringJoiner query, String name, String value) {
        query.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String encodeItemsWhenRequired(String items) {
        if (!StringUtils.hasText(items)) {
            return "";
        }
        String normalized = items.trim();
        if (containsNonAscii(normalized) || looksLikeJson(normalized)) {
            return Base64.getEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        }
        return normalized;
    }

    private boolean containsNonAscii(String value) {
        return value.chars().anyMatch(character -> character > 127);
    }

    private boolean looksLikeJson(String value) {
        return (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"));
    }

    private void requireConfiguration() {
        List<String> missing = properties.missingSettings();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KHQRPay is not configured; missing " + String.join(", ", missing));
        }
    }
}
