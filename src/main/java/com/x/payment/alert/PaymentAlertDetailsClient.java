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

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Looks up what a merchant wants to see in a payment alert: the store, the order and what was bought,
 * the customer and the cashier. Every part is optional; a lookup that fails is left out of the alert.
 */
@Component
public class PaymentAlertDetailsClient {
    private static final Logger log = LoggerFactory.getLogger(PaymentAlertDetailsClient.class);

    private final RestClient orderClient;
    private final RestClient customerClient;
    private final RestClient storeClient;
    private final RestClient userClient;

    public PaymentAlertDetailsClient(
            RestClient.Builder builder,
            @Value("${services.order.base-url:http://127.0.0.1:8083}") String orderBaseUrl,
            @Value("${services.customer.base-url:http://127.0.0.1:8091}") String customerBaseUrl,
            @Value("${services.store.base-url:http://127.0.0.1:8087}") String storeBaseUrl,
            @Value("${services.user.base-url:http://127.0.0.1:7445}") String userBaseUrl) {
        this.orderClient = createClient(builder, orderBaseUrl, "/api/v1/orders");
        this.customerClient = createClient(builder, customerBaseUrl, "/api/v1/customers");
        this.storeClient = createClient(builder, storeBaseUrl, "/api/v1/stores");
        this.userClient = createClient(builder, userBaseUrl, "/internal/users");
    }

    public PaymentAlertDetails find(QrPaymentReceived payment) {
        Long businessId = payment.businessId();
        if (businessId == null) return PaymentAlertDetails.EMPTY;
        JsonNode order = belongsTo(fetch(orderClient, "order", client -> client.get().uri("/{id}", payment.orderId())
                .retrieve().body(JsonNode.class), payment.orderId()), businessId);
        Long storeId = payment.storeId() != null ? payment.storeId()
                : order != null && order.hasNonNull("storeId") ? order.path("storeId").asLong() : null;
        Long cashierId = payment.cashierId() != null ? payment.cashierId()
                : order != null && order.hasNonNull("cashierId") ? order.path("cashierId").asLong() : null;

        JsonNode store = belongsTo(fetch(storeClient, "store", client -> client.get().uri("/{id}", storeId)
                .retrieve().body(JsonNode.class), storeId), businessId);
        JsonNode cashier = data(fetch(userClient, "cashier", client -> client.get()
                .uri(uri -> uri.path("/by-id/{id}").queryParam("businessId", businessId).build(cashierId))
                .retrieve().body(JsonNode.class), cashierId));

        return new PaymentAlertDetails(
                store == null ? null : clean(store.path("name").asText(null)),
                order == null ? null : clean(order.path("orderNo").asText(null)),
                order == null ? List.of() : items(order.path("items")),
                order == null ? null : customerName(order, businessId),
                cashier == null ? null : firstText(cashier, "fullName", "username"));
    }

    private String customerName(JsonNode order, long businessId) {
        long customerId = order.path("customerId").asLong(-1);
        if (customerId >= 0) {
            JsonNode customer = data(fetch(customerClient, "customer", client -> client.get()
                    .uri(uri -> uri.path("/{id}").queryParam("businessId", businessId).build(customerId))
                    .retrieve().body(JsonNode.class), customerId));
            String name = customer == null ? null : clean(customer.path("fullName").asText(null));
            if (name != null) return name;
        }
        return clean(order.path("deliveryAddress").path("receiverName").asText(null));
    }

    private static List<PaymentAlertDetails.Item> items(JsonNode items) {
        List<PaymentAlertDetails.Item> result = new ArrayList<>();
        if (!items.isArray()) return result;
        for (JsonNode item : items) {
            String name = clean(item.path("productName").asText(null));
            if (name == null) continue;
            String variant = clean(item.path("variantName").asText(null));
            if (variant != null && !variant.equalsIgnoreCase(name) && !variant.equalsIgnoreCase("default")) {
                name = name + " (" + variant + ")";
            }
            BigDecimal total = item.hasNonNull("total") ? item.path("total").decimalValue() : null;
            result.add(new PaymentAlertDetails.Item(name, item.path("quantity").asInt(1), total));
        }
        return result;
    }

    private static JsonNode fetch(RestClient client, String what, Function<RestClient, JsonNode> call, Long id) {
        if (id == null || id <= 0) return null;
        try {
            return call.apply(client);
        } catch (RestClientException exception) {
            log.warn("{} lookup for a payment alert failed; the alert will leave it out", what);
            return null;
        }
    }

    private static JsonNode data(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        return data == null || data.isMissingNode() || data.isNull() ? null : data;
    }

    /** Only data from the payment's own business is used. */
    private static JsonNode belongsTo(JsonNode response, long businessId) {
        JsonNode data = data(response);
        return data != null && data.path("businessId").asLong(-1) == businessId ? data : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = clean(node.path(field).asText(null));
            if (value != null) return value;
        }
        return null;
    }

    private static RestClient createClient(RestClient.Builder builder, String baseUrl, String path) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        String root = baseUrl.endsWith(path) ? baseUrl : baseUrl + path;
        return builder.clone().requestFactory(requestFactory).baseUrl(root).build();
    }

    static String clean(String text) {
        if (!StringUtils.hasText(text)) return null;
        String cleaned = text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
