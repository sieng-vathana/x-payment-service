package com.x.payment.repository;

import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;

import java.math.BigDecimal;

public interface PaymentBreakdownProjection {
    PaymentMethod getMethod();
    PaymentProvider getProvider();
    Long getPaymentCount();
    BigDecimal getTotalAmount();
    BigDecimal getRefundedAmount();
}
