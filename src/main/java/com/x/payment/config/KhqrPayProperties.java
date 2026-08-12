package com.x.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "khqrpay")
public class KhqrPayProperties {
    private String baseUrl = "https://khqr.cc";
    private String paymentRequestId;
    private String merchantSecret;
    private String successUrl;
    private String cancelUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPaymentRequestId() {
        return paymentRequestId;
    }

    public void setPaymentRequestId(String paymentRequestId) {
        this.paymentRequestId = paymentRequestId;
    }

    public String getMerchantSecret() {
        return merchantSecret;
    }

    public void setMerchantSecret(String merchantSecret) {
        this.merchantSecret = merchantSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, "KHQRPAY_BASE_URL", baseUrl);
        addIfMissing(missing, "KHQRPAY_PAYMENT_REQUEST_ID", paymentRequestId);
        addIfMissing(missing, "KHQRPAY_MERCHANT_SECRET", merchantSecret);
        addIfMissing(missing, "KHQRPAY_SUCCESS_URL", successUrl);
        return missing;
    }

    private void addIfMissing(List<String> missing, String setting, String value) {
        if (!StringUtils.hasText(value)) {
            missing.add(setting);
        }
    }
}
