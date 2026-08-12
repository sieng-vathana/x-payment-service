package com.x.payment.gateway;

import com.x.payment.config.KhqrPayProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KhqrPayGatewayTest {
    @Test
    void createsSignedManagedCheckoutUrl() {
        KhqrPayProperties properties = new KhqrPayProperties();
        properties.setPaymentRequestId("request-id");
        properties.setMerchantSecret("secret");
        properties.setSuccessUrl("https://shop.example/success");
        properties.setCancelUrl("https://shop.example/cancel");
        KhqrPayGateway gateway = new KhqrPayGateway(properties);

        QrPaymentInitiation initiation = gateway.initiate(
                "ORDER-1", new BigDecimal("15.00"), "Coffee");

        assertThat(initiation.checkoutUrl())
                .startsWith("https://khqr.cc/api/payment/request/request-id?")
                .contains("transaction_id=ORDER-1")
                .contains("amount=15.00")
                .contains("success_url=https%3A%2F%2Fshop.example%2Fsuccess")
                .contains("items=Coffee")
                .contains("cancel_url=https%3A%2F%2Fshop.example%2Fcancel")
                .contains("hash=3fbdf85398f5f3269985d7c29bc56bfa26141c5b");
    }
}
