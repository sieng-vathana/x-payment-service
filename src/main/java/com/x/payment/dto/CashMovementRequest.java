package com.x.payment.dto;

import com.x.payment.entity.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CashMovementRequest(
        @NotNull CashMovementType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 240) String reason,
        @NotNull @Positive Long createdBy) {
}
