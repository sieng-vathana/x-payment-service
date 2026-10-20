package com.x.payment.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Tells the merchant's open screens when a payment changes, after it is saved so they read the new state. */
@Component
public class PaymentRealtime {

    private final RealtimePublisher realtime;

    public PaymentRealtime(RealtimePublisher realtime) {
        this.realtime = realtime;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onStatusChanged(PaymentStatusChanged change) {
        realtime.toBusiness(change.businessId(), "payment.updated", Map.of(
                "paymentId", change.paymentId(),
                "orderId", change.orderId(),
                "storeId", change.storeId(),
                "status", change.status()));
    }

    /** The server could not ask Bakong about this QR; the POS shows the reason next to it. */
    public void checkFailed(Long businessId, Long paymentId, String message) {
        realtime.toBusiness(businessId, "payment.check_failed", Map.of("paymentId", paymentId, "message", message));
    }
}
