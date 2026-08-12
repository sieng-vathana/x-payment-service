package com.x.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KhqrPayWebhookRequest(
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("order_id") String orderId,
        String amount,
        @JsonProperty("paid_amount") String paidAmount,
        String status,
        @JsonProperty("req_time") String requestTime,
        String hash) {

    public String transactionReference() {
        return hasText(transactionId) ? transactionId.trim() : trim(orderId);
    }

    public String amountText() {
        return hasText(amount) ? amount.trim() : trim(paidAmount);
    }

    private static String trim(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
