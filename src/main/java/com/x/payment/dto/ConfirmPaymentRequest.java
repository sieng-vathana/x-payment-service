package com.x.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 160) String externalReference) {
}
