package com.x.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A KHQR checkout paid into the merchant's own Bakong account and confirmed by the cashier. */
public record CreateBakongQrPaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Size(max = 500) String note,
        @Positive Long cashierId,
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "[A-Za-z0-9._-]+@[A-Za-z0-9._-]+", message = "Bakong account ID must look like name@bank")
        String bakongAccountId,
        @NotBlank @Size(max = 160) String merchantName,
        @Size(max = 25) String billNumber) {
}
