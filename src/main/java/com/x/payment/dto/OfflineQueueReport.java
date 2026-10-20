package com.x.payment.dto;

import com.x.payment.entity.PosOfflineQueue;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What a POS device says it is holding offline. The business comes from the signed-in user, not from the device. */
public record OfflineQueueReport(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @Positive Long cashierId,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
        @Min(0) @Max(10000) int waitingCount,
        @DecimalMin("0.00") BigDecimal waitingTotal,
        @Min(0) @Max(10000) int attentionCount) {

    public PosOfflineQueue toEntity(String deviceId, LocalDateTime now) {
        return PosOfflineQueue.builder()
                .deviceId(deviceId).businessId(businessId).storeId(storeId).cashierId(cashierId)
                .currencyCode(currencyCode.toUpperCase()).waitingCount(waitingCount)
                .waitingTotal(waitingTotal == null ? BigDecimal.ZERO : waitingTotal.setScale(2, java.math.RoundingMode.HALF_UP))
                .attentionCount(attentionCount).reportedAt(now).build();
    }
}
