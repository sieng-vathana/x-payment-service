package com.x.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bakong Open API settings. The token comes from the BAKONG_API_TOKEN environment variable;
 * without it, KHQR payments are confirmed by the cashier instead of automatically.
 */
@ConfigurationProperties(prefix = "bakong.api")
public class BakongApiProperties {
    private String baseUrl = "https://api-bakong.nbc.gov.kh";
    private String token;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
