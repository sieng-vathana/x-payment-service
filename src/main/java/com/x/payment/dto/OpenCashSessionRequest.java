package com.x.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OpenCashSessionRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long cashierId,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal openingFloat,
        @Size(max = 500) String note) {
}
