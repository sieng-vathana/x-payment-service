package com.x.payment.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAlertDetailsClientTest {
    private HttpServer server;
    private PaymentAlertDetailsClient client;
    private final Map<String, String> responses = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestURI().getPath()
                    + (exchange.getRequestURI().getQuery() == null ? "" : "?" + exchange.getRequestURI().getQuery());
            String body = responses.get(key);
            byte[] bytes = (body == null ? "{\"status\":0,\"code\":404}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new PaymentAlertDetailsClient(RestClient.builder(), base, base, base, base);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static QrPaymentReceived payment(Long businessId, Long storeId, Long orderId) {
        return new QrPaymentReceived(1L, businessId, storeId, orderId, null, new BigDecimal("7.50"), "USD", null, false,
                null, null, LocalDateTime.now());
    }

    @Test
    void collectsStoreOrderItemsCustomerAndCashier() {
        responses.put("/api/v1/orders/40", """
                {"data":{"id":40,"orderNo":"POS-0012","businessId":2,"storeId":3,"customerId":5,"cashierId":9,
                 "items":[{"productName":"Iced latte","variantName":"Large","quantity":2,"total":5.00},
                          {"productName":"Croissant","variantName":"Default","quantity":1,"total":2.50}]}}""");
        responses.put("/api/v1/stores/3", "{\"data\":{\"id\":3,\"businessId\":2,\"name\":\"Main Store\"}}");
        responses.put("/api/v1/customers/5?businessId=2", "{\"data\":{\"fullName\":\" Sok  Dara \"}}");
        responses.put("/internal/users/by-id/9?businessId=2", "{\"data\":{\"username\":\"vathana\",\"fullName\":\"Vathana S\"}}");

        PaymentAlertDetails details = client.find(payment(2L, null, 40L));

        assertThat(details.storeName()).isEqualTo("Main Store");
        assertThat(details.orderNo()).isEqualTo("POS-0012");
        assertThat(details.items()).containsExactly(
                new PaymentAlertDetails.Item("Iced latte (Large)", 2, new BigDecimal("5.0")),
                new PaymentAlertDetails.Item("Croissant", 1, new BigDecimal("2.5")));
        assertThat(details.customerName()).isEqualTo("Sok Dara");
        assertThat(details.cashierName()).isEqualTo("Vathana S");
    }

    @Test
    void ignoresAnotherBusinessesDataAndMissingServices() {
        responses.put("/api/v1/orders/40", "{\"data\":{\"id\":40,\"orderNo\":\"X-1\",\"businessId\":99}}");
        responses.put("/api/v1/stores/3", "{\"data\":{\"id\":3,\"businessId\":99,\"name\":\"Someone else\"}}");

        PaymentAlertDetails details = client.find(payment(2L, 3L, 40L));

        assertThat(details).isEqualTo(new PaymentAlertDetails(null, null, java.util.List.of(), null, null));
    }
}
