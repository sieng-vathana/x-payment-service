package com.x.payment.dto;

import com.x.payment.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id, Long orderId, Long businessId, Long storeId, BigDecimal amount,
        BigDecimal tenderedAmount, BigDecimal changeAmount, BigDecimal refundedAmount,
        String currencyCode, PaymentMethod method, PaymentProvider provider, PaymentStatus status,
        String externalReference, String idempotencyKey, String note,
        LocalDateTime paidAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getOrderId(), payment.getBusinessId(), payment.getStoreId(),
                payment.getAmount(), payment.getTenderedAmount(), payment.getChangeAmount(),
                payment.getRefundedAmount(), payment.getCurrencyCode(), payment.getMethod(),
                payment.getProvider(), payment.getStatus(), payment.getExternalReference(),
                payment.getIdempotencyKey(), payment.getNote(), payment.getPaidAt(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
