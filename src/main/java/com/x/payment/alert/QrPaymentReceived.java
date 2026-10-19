package com.x.payment.alert;

import com.x.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A KHQR payment that became PAID, published so the merchant's Telegram chat hears about it.
 * payerAccount and reference come from Bakong when the bank confirmed the payment; they are null otherwise.
 */
public record QrPaymentReceived(
        Long paymentId,
        Long businessId,
        Long storeId,
        Long orderId,
        Long cashierId,
        BigDecimal amount,
        String currencyCode,
        String note,
        boolean confirmedByCashier,
        String payerAccount,
        String reference,
        LocalDateTime paidAt) {

    public static QrPaymentReceived from(Payment payment, boolean confirmedByCashier) {
        return from(payment, confirmedByCashier, null, payment.getExternalReference());
    }

    public static QrPaymentReceived from(Payment payment, boolean confirmedByCashier, String payerAccount, String reference) {
        return new QrPaymentReceived(
                payment.getId(), payment.getBusinessId(), payment.getStoreId(), payment.getOrderId(), payment.getCashierId(),
                payment.getAmount(), payment.getCurrencyCode(), payment.getNote(), confirmedByCashier,
                payerAccount, reference, payment.getPaidAt());
    }
}
