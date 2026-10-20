package com.x.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The latest report from one POS device about the cash sales it is holding offline. One row per device. */
@Entity
@Table(name = "pos_offline_queue", indexes = {
        @Index(name = "idx_pos_offline_queue_business", columnList = "business_id,reported_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosOfflineQueue {

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "waiting_count", nullable = false)
    private int waitingCount;

    @Column(name = "waiting_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal waitingTotal;

    @Column(name = "attention_count", nullable = false)
    private int attentionCount;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;
}
