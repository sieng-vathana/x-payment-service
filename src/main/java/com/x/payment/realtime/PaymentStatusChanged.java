package com.x.payment.realtime;

import com.x.payment.entity.Payment;

/** A payment was paid or refunded; screens showing it are told once the change is committed. */
public record PaymentStatusChanged(Long paymentId, Long orderId, Long businessId, Long storeId, String status) {

    public static PaymentStatusChanged of(Payment payment) {
        return new PaymentStatusChanged(payment.getId(), payment.getOrderId(), payment.getBusinessId(),
                payment.getStoreId(), payment.getStatus().name());
    }
}
