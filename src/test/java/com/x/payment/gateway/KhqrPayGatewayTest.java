package com.x.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.payment.config.KhqrPayProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KhqrPayGatewayTest {
    private HttpServer providerServer;

    @AfterEach
    void stopProviderServer() {
        if (providerServer != null) {
            providerServer.stop(0);
        }
    }

    @Test
    void postsSignedQrRequestAndReturnsQrData() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(
                200,
                """
                        {"responseCode":0,"responseMessage":"Success","data":{
                          "transaction_id":"ORDER-1",
                          "amount":"15.00",
                          "qr":"khqr-payload",
                          "qr_url":"https://khqr.cc/api/khqr/ORDER-1",
                          "md5":"payment-md5"
                        }}
                        """,
                providerCalls,
                capturedRequest));
        properties.setPaymentRequestId(" request-id ");
        properties.setMerchantSecret(" secret ");
        properties.setSuccessUrl(" https://shop.example/success ");
        properties.setCancelUrl("https://shop.example/cancel");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        QrPaymentInitiation initiation = gateway.initiate(
                "ORDER-1", new BigDecimal("15.00"), "Coffee");

        assertThat(providerCalls).hasValue(1);
        assertThat(capturedRequest.get().method()).isEqualTo("POST");
        assertThat(capturedRequest.get().contentType()).isEqualTo("application/json");
        JsonNode requestBody = new ObjectMapper().readTree(capturedRequest.get().body());
        assertThat(requestBody.get("transaction_id").asText()).isEqualTo("ORDER-1");
        assertThat(requestBody.get("amount").asText()).isEqualTo("15.00");
        assertThat(requestBody.get("success_url").asText()).isEqualTo("https://shop.example/success");
        assertThat(requestBody.get("remark").asText()).isEqualTo("Coffee");
        assertThat(requestBody.get("hash").asText())
                .isEqualTo("3fbdf85398f5f3269985d7c29bc56bfa26141c5b");
        assertThat(initiation.transactionId()).isEqualTo("ORDER-1");
        assertThat(initiation.qrPayload()).isEqualTo("khqr-payload");
        assertThat(initiation.qrImageUrl()).isEqualTo("https://khqr.cc/api/khqr/ORDER-1");
        assertThat(initiation.checkoutUrl()).isNull();
    }

    @Test
    void resolvesRelativeCallbackPathsBeforeSigningAndSending() throws IOException {
        AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(
                200,
                "{\"responseCode\":0,\"responseMessage\":\"Success\",\"data\":{\"qr\":\"payload\"}}",
                new AtomicInteger(),
                capturedRequest));
        properties.setPaymentRequestId("request-id");
        properties.setMerchantSecret("secret");
        properties.setSuccessUrl("/sales/payments");
        properties.setCancelUrl("/sales/payments");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        gateway.initiate(
                "XP-1555-9f18694ed569a8db", new BigDecimal("1.00"), null);

        JsonNode requestBody = new ObjectMapper().readTree(capturedRequest.get().body());
        assertThat(requestBody.get("success_url").asText())
                .isEqualTo("https://portal.learner-teach.online/sales/payments");
        assertThat(requestBody.get("remark").asText()).isEmpty();
        assertThat(requestBody.get("hash").asText())
                .isEqualTo("3a97b18212e9563e3020c1320acc3a75356bdd51");
    }

    @Test
    void rejectsCheckoutWhenKhqrPayReturnsAnErrorResponse() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(
                403,
                "{\"responseCode\":1,\"responseMessage\":\"Invalid Security Hash\"}",
                providerCalls,
                new AtomicReference<>()));
        properties.setPaymentRequestId("request-id");
        properties.setMerchantSecret("secret");
        properties.setSuccessUrl("https://shop.example/success");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        assertThatThrownBy(() -> gateway.initiate("ORDER-1", new BigDecimal("15.00"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid Security Hash");
        assertThat(providerCalls).hasValue(1);
    }

    @Test
    void createsGatewayThroughSpringUsingTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KhqrPayProperties.class);
            context.register(KhqrPayGateway.class);
            context.refresh();

            assertThat(context.getBean(KhqrPayGateway.class)).isNotNull();
        }
    }

    private String startProviderServer(
            int status,
            String body,
            AtomicInteger calls,
            AtomicReference<CapturedRequest> capturedRequest) throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/api/request-id/payment-gateway/v1/payments/qr-api", exchange -> {
            calls.incrementAndGet();
            capturedRequest.set(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        providerServer.start();
        return "http://127.0.0.1:" + providerServer.getAddress().getPort();
    }

    private record CapturedRequest(String method, String contentType, String body) {
    }
}
