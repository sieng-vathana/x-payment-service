package com.x.payment.dto;

import java.math.BigDecimal;

/** Payments taken in one hour, day or month of the viewer's time zone, and how much of them was refunded. */
public record PaymentPeriodResponse(String period, long paymentCount, BigDecimal totalAmount, BigDecimal refundedAmount) {
}
