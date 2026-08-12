package com.x.payment.gateway;

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
    void createsSignedManagedCheckoutUrl() throws IOException {
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(200, "<html>Checkout</html>", new AtomicInteger()));
        properties.setPaymentRequestId(" request-id ");
        properties.setMerchantSecret(" secret ");
        properties.setSuccessUrl(" https://shop.example/success ");
        properties.setCancelUrl("https://shop.example/cancel");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        QrPaymentInitiation initiation = gateway.initiate(
                "ORDER-1", new BigDecimal("15.00"), "Coffee");

        assertThat(initiation.checkoutUrl())
                .startsWith(properties.getBaseUrl() + "/api/payment/request/request-id?")
                .contains("transaction_id=ORDER-1")
                .contains("amount=15.00")
                .contains("success_url=https%3A%2F%2Fshop.example%2Fsuccess")
                .contains("items=Coffee")
                .contains("cancel_url=https%3A%2F%2Fshop.example%2Fcancel")
                .contains("hash=3fbdf85398f5f3269985d7c29bc56bfa26141c5b");
    }

    @Test
    void resolvesRelativeCallbackPathsBeforeSigningAndSending() throws IOException {
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(200, "<html>Checkout</html>", new AtomicInteger()));
        properties.setPaymentRequestId("request-id");
        properties.setMerchantSecret("secret");
        properties.setSuccessUrl("/sales/payments");
        properties.setCancelUrl("/sales/payments");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        QrPaymentInitiation initiation = gateway.initiate(
                "XP-1555-9f18694ed569a8db", new BigDecimal("1.00"), null);

        assertThat(initiation.checkoutUrl())
                .contains("success_url=https%3A%2F%2Fportal.learner-teach.online%2Fsales%2Fpayments")
                .contains("cancel_url=https%3A%2F%2Fportal.learner-teach.online%2Fsales%2Fpayments")
                .contains("hash=3a97b18212e9563e3020c1320acc3a75356bdd51");
    }

    @Test
    void rejectsCheckoutWhenKhqrPayReturnsAnErrorResponse() throws IOException {
        AtomicInteger providerCalls = new AtomicInteger();
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setBaseUrl(startProviderServer(
                403,
                "{\"responseCode\":1,\"responseMessage\":\"Invalid Security Hash\"}",
                providerCalls));
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

    private String startProviderServer(int status, String body, AtomicInteger calls) throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/api/payment/request/request-id", exchange -> {
            calls.incrementAndGet();
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        providerServer.start();
        return "http://127.0.0.1:" + providerServer.getAddress().getPort();
    }
}
