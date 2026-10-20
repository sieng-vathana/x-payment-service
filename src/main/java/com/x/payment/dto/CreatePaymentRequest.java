package com.x.payment.dto;

import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @DecimalMin(value = "0.00") BigDecimal tenderedAmount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotNull PaymentMethod method,
        @NotNull PaymentProvider provider,
        @Size(max = 160) String externalReference,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Size(max = 500) String note,
        @Positive Long cashierId,
        /** For a cash sale taken offline: when it happened at the till (UTC). Counted in that sale's cash register. */
        LocalDateTime soldAt) {

    public CreatePaymentRequest(
            Long orderId, Long businessId, Long storeId, BigDecimal amount, BigDecimal tenderedAmount, String currencyCode,
            PaymentMethod method, PaymentProvider provider, String externalReference, String idempotencyKey, String note,
            Long cashierId) {
        this(orderId, businessId, storeId, amount, tenderedAmount, currencyCode, method, provider, externalReference,
                idempotencyKey, note, cashierId, null);
    }
}
