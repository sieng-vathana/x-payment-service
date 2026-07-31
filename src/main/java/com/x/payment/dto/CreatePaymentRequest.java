package com.x.payment.dto;

import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

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
        @Size(max = 500) String note) {
}
