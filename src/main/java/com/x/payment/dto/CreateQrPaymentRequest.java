package com.x.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateQrPaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Size(max = 500) String note) {
}
