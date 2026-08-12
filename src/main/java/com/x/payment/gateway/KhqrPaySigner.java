package com.x.payment.gateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class KhqrPaySigner {
    public String sign(
            String merchantSecret,
            String transactionId,
            BigDecimal amount,
            String successUrl,
            String remark) {
        String payload = merchantSecret
                + transactionId
                + amount.toPlainString()
                + successUrl
                + remark;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }
}
