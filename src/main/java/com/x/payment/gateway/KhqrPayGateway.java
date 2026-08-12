package com.x.payment.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.payment.config.KhqrPayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class KhqrPayGateway implements QrPaymentGateway {
    private final KhqrPayProperties properties;
    private final KhqrPaySigner signer = new KhqrPaySigner();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
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
    public QrPaymentInitiation initiate(String transactionId, BigDecimal amount, String remark) {
        requireConfiguration();
        String merchantSecret = properties.getMerchantSecret().trim();
        String successUrl = absoluteCallbackUrl(properties.getSuccessUrl());
        String paymentRequestId = properties.getPaymentRequestId().trim();
        String normalizedRemark = StringUtils.hasText(remark) ? remark.trim() : "";
        String hash = signer.sign(
                merchantSecret, transactionId, amount, successUrl, normalizedRemark);

        URI endpoint = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment("api", paymentRequestId, "payment-gateway", "v1", "payments", "qr-api")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        KhqrPayRequest providerRequest = new KhqrPayRequest(
                transactionId,
                amount.toPlainString(),
                successUrl,
                normalizedRemark,
                hash);

        return requestQr(endpoint, providerRequest);
    }

    private QrPaymentInitiation requestQr(URI endpoint, KhqrPayRequest providerRequest) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(providerRequest);
        } catch (JsonProcessingException exception) {
            throw providerUnavailable(exception);
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            KhqrPayResponse providerResponse = parseProviderResponse(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = StringUtils.hasText(providerResponse.responseMessage())
                        ? providerResponse.responseMessage()
                        : "HTTP " + response.statusCode();
                throw rejectedCheckout(message);
            }
            if (providerResponse.responseCode() == null) {
                throw rejectedCheckout("Invalid response from provider");
            }
            if (providerResponse.responseCode() != 0) {
                throw rejectedCheckout(providerResponse.responseMessage());
            }
            if (!StringUtils.hasText(providerResponse.qr())) {
                throw rejectedCheckout("Provider did not return a QR payload");
            }

            return new QrPaymentInitiation(
                    providerRequest.transactionId(),
                    providerResponse.qr(),
                    providerResponse.qrUrl(),
                    null,
                    null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable(exception);
        } catch (IOException exception) {
            throw providerUnavailable(exception);
        }
    }

    private KhqrPayResponse parseProviderResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return new KhqrPayResponse(null, null, null, null);
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode responseCode = response.get("responseCode");
            JsonNode responseMessage = response.get("responseMessage");
            JsonNode data = response.get("data");
            return new KhqrPayResponse(
                    responseCode == null || responseCode.isNull() ? null : responseCode.asInt(),
                    responseMessage == null || responseMessage.isNull() ? null : responseMessage.asText(),
                    textValue(data, "qr"),
                    textValue(data, "qr_url"));
        } catch (IOException ignored) {
            return new KhqrPayResponse(null, null, null, null);
        }
    }

    private String textValue(JsonNode parent, String fieldName) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        JsonNode value = parent.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
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

    private record KhqrPayRequest(
            @JsonProperty("transaction_id") String transactionId,
            String amount,
            @JsonProperty("success_url") String successUrl,
            String remark,
            String hash) {
    }

    private record KhqrPayResponse(
            Integer responseCode,
            String responseMessage,
            String qr,
            String qrUrl) {
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

    private void requireConfiguration() {
        List<String> missing = properties.missingSettings();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KHQRPay is not configured; missing " + String.join(", ", missing));
        }
    }
}
