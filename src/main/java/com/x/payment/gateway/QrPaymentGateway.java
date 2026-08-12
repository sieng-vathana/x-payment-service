package com.x.payment.gateway;

import java.math.BigDecimal;

public interface QrPaymentGateway {
    QrPaymentInitiation initiate(String transactionId, BigDecimal amount, String remark);
}
