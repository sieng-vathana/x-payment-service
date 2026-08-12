package com.x.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KhqrPaySignerTest {
    private final KhqrPaySigner signer = new KhqrPaySigner();

    @Test
    void signsSecretIdAmountUrlAndRemarkWithSha1() {
        String signature = signer.sign(
                "secret", "ORDER-1", new BigDecimal("15.00"),
                "https://shop.example/success", "Coffee");

        assertThat(signature).isEqualTo("3fbdf85398f5f3269985d7c29bc56bfa26141c5b");
    }
}
