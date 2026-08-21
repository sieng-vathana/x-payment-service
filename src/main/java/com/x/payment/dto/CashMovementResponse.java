package com.x.payment.dto;

import com.x.payment.entity.CashMovement;
import com.x.payment.entity.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashMovementResponse(
        Long id,
        Long sessionId,
        CashMovementType type,
        BigDecimal amount,
        String reason,
        Long createdBy,
        LocalDateTime createdAt) {
    public static CashMovementResponse from(CashMovement movement) {
        return new CashMovementResponse(
                movement.getId(), movement.getSessionId(), movement.getType(), movement.getAmount(),
                movement.getReason(), movement.getCreatedBy(), movement.getCreatedAt());
    }
}
