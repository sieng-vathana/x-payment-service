package com.x.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloseCashSessionRequest(
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal countedCash,
        @NotNull @Positive Long closedBy,
        @Size(max = 500) String closeNote) {
}
