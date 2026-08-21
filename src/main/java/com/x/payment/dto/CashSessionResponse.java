package com.x.payment.dto;

import com.x.payment.entity.CashSessionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CashSessionResponse(
        Long id,
        Long businessId,
        Long storeId,
        Long cashierId,
        String currencyCode,
        CashSessionStatus status,
        BigDecimal openingFloat,
        BigDecimal cashSales,
        BigDecimal cashRefunds,
        BigDecimal cashIn,
        BigDecimal cashOut,
        long paymentCount,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        Long openedBy,
        Long closedBy,
        String note,
        String closeNote,
        List<CashMovementResponse> movements) {
}
