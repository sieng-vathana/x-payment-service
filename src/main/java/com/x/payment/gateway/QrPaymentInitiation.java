package com.x.payment.gateway;

public record QrPaymentInitiation(
        String transactionId,
        String qrPayload,
        String qrImageUrl,
        String checkoutUrl,
        String expiresAt) {
}
