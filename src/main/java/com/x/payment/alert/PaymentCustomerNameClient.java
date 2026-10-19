package com.x.payment.alert;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Resolves the order's customer name when a POS or online KHQR payment is confirmed. */
@Component
public class PaymentCustomerNameClient {
    private static final Logger log = LoggerFactory.getLogger(PaymentCustomerNameClient.class);

    private final RestClient orderClient;
    private final RestClient customerClient;

    public PaymentCustomerNameClient(
            RestClient.Builder builder,
            @Value("${services.order.base-url:http://127.0.0.1:8083}") String orderBaseUrl,
            @Value("${services.customer.base-url:http://127.0.0.1:8091}") String customerBaseUrl) {
        this.orderClient = createClient(builder, orderBaseUrl, "/api/v1/orders");
        this.customerClient = createClient(builder, customerBaseUrl, "/api/v1/customers");
    }

    public String findName(Long businessId, Long orderId) {
        if (businessId == null || orderId == null || orderId <= 0) {
            return null;
        }
        JsonNode response;
        try {
            response = orderClient.get().uri("/{id}", orderId).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Order lookup failed; Telegram alert will omit the customer name");
            return null;
        }
        JsonNode order = response == null ? null : response.path("data");
        if (order == null || order.isMissingNode() || order.path("businessId").asLong(-1) != businessId) {
            return null;
        }

        long customerId = order.path("customerId").asLong(-1);
        if (customerId >= 0) {
            try {
                JsonNode customerResponse = customerClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/{id}")
                                .queryParam("businessId", businessId)
                                .build(customerId))
                        .retrieve()
                        .body(JsonNode.class);
                String customerName = customerResponse == null
                        ? null
                        : cleanName(customerResponse.path("data").path("fullName").asText(null));
                if (customerName != null) {
                    return customerName;
                }
            } catch (RestClientException exception) {
                log.warn("Customer lookup failed; Telegram alert will use the delivery recipient when available");
            }
        }
        return cleanName(order.path("deliveryAddress").path("receiverName").asText(null));
    }

    private static RestClient createClient(RestClient.Builder builder, String baseUrl, String path) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.clone().requestFactory(requestFactory).baseUrl(baseUrl + path).build();
    }

    private static String cleanName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
