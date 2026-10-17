package com.x.payment.gateway;

import com.sun.net.httpserver.HttpServer;
import com.x.payment.config.BakongApiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BakongTransactionClientTest {
    private HttpServer bakongServer;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @AfterEach
    void stopBakongServer() {
        if (bakongServer != null) {
            bakongServer.stop(0);
        }
    }

    @Test
    void returnsTheTransactionOnceTheKhqrIsPaid() throws IOException {
        BakongTransactionClient client = clientFor(200, """
                {"responseCode":0,"responseMessage":"Getting transaction successfully.","errorCode":null,
                 "data":{"hash":"abc123","fromAccountId":"customer@aba","toAccountId":"tinhlok_shop@aclb",
                         "currency":"USD","amount":12.5}}
                """);

        Optional<BakongTransactionClient.BakongTransaction> transaction = client.findByMd5("d41d8cd98f00b204e9800998ecf8427e");

        assertThat(transaction).isPresent();
        assertThat(transaction.get().toAccountId()).isEqualTo("tinhlok_shop@aclb");
        assertThat(transaction.get().settles(new BigDecimal("12.50"), "USD")).isTrue();
        assertThat(requestBody.get()).isEqualTo("{\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}");
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void returnsEmptyWhileTheKhqrIsUnpaid() throws IOException {
        BakongTransactionClient client = clientFor(200, """
                {"responseCode":1,"responseMessage":"Transaction could not be found. Please check and try again.",
                 "errorCode":1,"data":null}
                """);

        assertThat(client.findByMd5("d41d8cd98f00b204e9800998ecf8427e")).isEmpty();
    }

    @Test
    void explainsWhenBakongRefusesTheRequest() throws IOException {
        BakongTransactionClient client = clientFor(403, "Forbidden");

        assertThatThrownBy(() -> client.findByMd5("d41d8cd98f00b204e9800998ecf8427e"))
                .isInstanceOf(BakongTransactionClient.BakongApiException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void reportsTheBakongMessageForOtherErrors() throws IOException {
        BakongTransactionClient client = clientFor(200, """
                {"responseCode":1,"responseMessage":"Unauthorized, not yet requested for token or code invalid",
                 "errorCode":6,"data":null}
                """);

        assertThatThrownBy(() -> client.findByMd5("d41d8cd98f00b204e9800998ecf8427e"))
                .isInstanceOf(BakongTransactionClient.BakongApiException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void isOffWithoutAToken() {
        BakongTransactionClient client = new BakongTransactionClient(new BakongApiProperties());

        assertThatThrownBy(() -> client.findByMd5("d41d8cd98f00b204e9800998ecf8427e"))
                .isInstanceOf(BakongTransactionClient.BakongApiException.class)
                .hasMessageContaining("no API token");
    }

    @Test
    void settlesOnlyTheExpectedAmountAndCurrency() {
        BakongTransactionClient.BakongTransaction usd =
                new BakongTransactionClient.BakongTransaction("h", new BigDecimal("3.00"), "USD", null, null);
        BakongTransactionClient.BakongTransaction riel =
                new BakongTransactionClient.BakongTransaction("h", new BigDecimal("10500"), "KHR", null, null);

        assertThat(usd.settles(new BigDecimal("3.00"), "USD")).isTrue();
        assertThat(usd.settles(new BigDecimal("3.01"), "USD")).isFalse();
        assertThat(usd.settles(new BigDecimal("3.00"), "KHR")).isFalse();
        assertThat(riel.settles(new BigDecimal("10500.40"), "KHR")).isTrue();
    }

    private BakongTransactionClient clientFor(int status, String body) throws IOException {
        bakongServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bakongServer.createContext("/v1/check_transaction_by_md5", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        bakongServer.start();
        BakongApiProperties properties = new BakongApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + bakongServer.getAddress().getPort());
        properties.setToken("test-token");
        return new BakongTransactionClient(properties);
    }
}
