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

    public boolean verifyWebhook(
            String merchantSecret,
            String transactionId,
            String amount,
            String status,
            String requestTime,
            String providedHash) {
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }

        String expectedHash = signWebhook(merchantSecret, transactionId, amount, status, requestTime);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                providedHash.trim().getBytes(StandardCharsets.UTF_8));
    }

    String signWebhook(
            String merchantSecret,
            String transactionId,
            String amount,
            String status,
            String requestTime) {
        String payload = merchantSecret + transactionId + amount + status
                + (requestTime == null ? "" : requestTime);
        return sha1(payload);
    }

    private String sha1(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        }
    }
}
