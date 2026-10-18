package com.x.payment.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Hands alerts to the business service, which sends them to the merchant's Telegram chat. */
@Component
public class BusinessAlertClient {

    private static final Logger log = LoggerFactory.getLogger(BusinessAlertClient.class);
    private final RestClient client;

    public BusinessAlertClient(
            RestClient.Builder builder,
            @Value("${services.business.base-url:http://127.0.0.1:8086}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // The business service waits for Telegram before it answers.
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl + "/internal/businesses")
                .build();
    }

    public void send(Long businessId, String type, String text) {
        try {
            client.post()
                    .uri("/{id}/telegram/notify", businessId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", type, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram alert for business {} was not sent: {}", businessId, exception.getMessage());
        }
    }
}
