package com.x.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_sessions", indexes = {
        @Index(name = "idx_cash_session_store_cashier_status", columnList = "store_id,cashier_id,status"),
        @Index(name = "idx_cash_session_store_opened", columnList = "store_id,opened_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CashSessionStatus status;

    @Column(name = "opening_float", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingFloat;
    @Column(name = "expected_cash", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedCash;
    @Column(name = "counted_cash", precision = 14, scale = 2)
    private BigDecimal countedCash;
    @Column(precision = 14, scale = 2)
    private BigDecimal variance;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "opened_by", nullable = false)
    private Long openedBy;
    @Column(name = "closed_by")
    private Long closedBy;
    @Column(length = 500)
    private String note;
    @Column(name = "close_note", length = 500)
    private String closeNote;

    @Version
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
