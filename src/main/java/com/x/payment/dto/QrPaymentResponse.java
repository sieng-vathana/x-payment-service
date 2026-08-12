package com.x.payment.dto;

import com.x.payment.gateway.QrPaymentInitiation;

public record QrPaymentResponse(
        PaymentResponse payment,
        String transactionId,
        String qrPayload,
        String qrImageUrl,
        String checkoutUrl,
        String expiresAt,
        boolean reused) {
    public static QrPaymentResponse created(PaymentResponse payment, QrPaymentInitiation initiation) {
        return new QrPaymentResponse(
                payment, initiation.transactionId(), initiation.qrPayload(), initiation.qrImageUrl(),
                initiation.checkoutUrl(), initiation.expiresAt(), false);
    }

    public static QrPaymentResponse reused(PaymentResponse payment, QrPaymentInitiation initiation) {
        return new QrPaymentResponse(
                payment, initiation.transactionId(), initiation.qrPayload(), initiation.qrImageUrl(),
                initiation.checkoutUrl(), initiation.expiresAt(), true);
    }
}
