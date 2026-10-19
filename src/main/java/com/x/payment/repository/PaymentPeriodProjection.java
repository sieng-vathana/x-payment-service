package com.x.payment.repository;

import java.math.BigDecimal;

public interface PaymentPeriodProjection {
    String getPeriod();
    Long getPaymentCount();
    BigDecimal getTotalAmount();
    BigDecimal getRefundedAmount();
}
