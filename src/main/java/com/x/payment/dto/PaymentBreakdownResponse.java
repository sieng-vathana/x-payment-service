package com.x.payment.dto;

import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;

import java.math.BigDecimal;

public record PaymentBreakdownResponse(
        PaymentMethod method,
        PaymentProvider provider,
        long paymentCount,
        BigDecimal totalAmount,
        BigDecimal refundedAmount) {
}
