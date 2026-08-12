package com.x.payment.gateway;

import com.x.payment.config.KhqrPayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.StringJoiner;

@Component
public class KhqrPayGateway implements QrPaymentGateway {
    private final KhqrPayProperties properties;
    private final KhqrPaySigner signer = new KhqrPaySigner();

    public KhqrPayGateway(KhqrPayProperties properties) {
        this.properties = properties;
    }

    @Override
    public QrPaymentInitiation initiate(String transactionId, BigDecimal amount, String items) {
        requireConfiguration();
        String encodedItems = encodeItemsWhenRequired(items);
        String hash = signer.sign(
                properties.getMerchantSecret(), transactionId, amount,
                properties.getSuccessUrl(), encodedItems);

        String endpoint = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment("api", "payment", "request", properties.getPaymentRequestId())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        StringJoiner query = new StringJoiner("&");
        addQueryParameter(query, "transaction_id", transactionId);
        addQueryParameter(query, "amount", amount.toPlainString());
        addQueryParameter(query, "success_url", properties.getSuccessUrl());
        addQueryParameter(query, "hash", hash);
        if (StringUtils.hasText(encodedItems)) {
            addQueryParameter(query, "items", encodedItems);
        }
        if (StringUtils.hasText(properties.getCancelUrl())) {
            addQueryParameter(query, "cancel_url", properties.getCancelUrl().trim());
        }

        return new QrPaymentInitiation(
                transactionId,
                null,
                null,
                endpoint + "?" + query,
                null);
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
