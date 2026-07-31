package com.x.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_store_created", columnList = "store_id,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(name = "tendered_amount", precision = 14, scale = 2)
    private BigDecimal tenderedAmount;
    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;
    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundedAmount;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentMethod method;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentProvider provider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;

    @Column(name = "external_reference", length = 160)
    private String externalReference;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
    @Column(length = 500)
    private String note;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
