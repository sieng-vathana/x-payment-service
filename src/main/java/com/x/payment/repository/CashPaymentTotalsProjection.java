package com.x.payment.repository;

import java.math.BigDecimal;

public interface CashPaymentTotalsProjection {
    BigDecimal getGrossAmount();
    BigDecimal getRefundedAmount();
    long getPaymentCount();
}
