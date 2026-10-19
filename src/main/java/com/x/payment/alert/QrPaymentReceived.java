package com.x.payment.alert;

import com.x.payment.entity.Payment;

import java.math.BigDecimal;

/** A KHQR payment that became PAID, published so the merchant's Telegram chat hears about it. */
public record QrPaymentReceived(
        Long paymentId,
        Long businessId,
        Long storeId,
        BigDecimal amount,
        String currencyCode,
        String note,
        boolean confirmedByCashier) {

    public static QrPaymentReceived from(Payment payment, boolean confirmedByCashier) {
        return new QrPaymentReceived(
                payment.getId(), payment.getBusinessId(), payment.getStoreId(), payment.getAmount(),
                payment.getCurrencyCode(), payment.getNote(), confirmedByCashier);
    }
}
