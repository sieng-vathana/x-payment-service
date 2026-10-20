package com.x.payment.repository;

import com.x.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByExternalReference(String externalReference);
    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    List<Payment> findByProviderAndMethodAndStatusAndCreatedAtAfter(
            com.x.payment.entity.PaymentProvider provider, com.x.payment.entity.PaymentMethod method,
            com.x.payment.entity.PaymentStatus status, java.time.LocalDateTime createdAfter);

    @Query("""
            select coalesce(sum(p.amount), 0) as grossAmount,
                   coalesce(sum(p.refundedAmount), 0) as refundedAmount,
                   count(p.id) as paymentCount
            from Payment p
            where p.storeId = :storeId
              and p.cashierId = :cashierId
              and p.currencyCode = :currencyCode
              and p.method = com.x.payment.entity.PaymentMethod.CASH
              and coalesce(p.paidAt, p.createdAt) >= :from
              and coalesce(p.paidAt, p.createdAt) < :to
              and p.status in (com.x.payment.entity.PaymentStatus.PAID,
                               com.x.payment.entity.PaymentStatus.PARTIALLY_REFUNDED,
                               com.x.payment.entity.PaymentStatus.REFUNDED)
            """)
    CashPaymentTotalsProjection cashPaymentTotals(
            @Param("storeId") Long storeId,
            @Param("cashierId") Long cashierId,
            @Param("currencyCode") String currencyCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            select p.method as method, p.provider as provider, count(p.id) as paymentCount,
                   coalesce(sum(p.amount), 0) as totalAmount,
                   coalesce(sum(p.refundedAmount), 0) as refundedAmount
            from Payment p
            where p.storeId = :storeId
              and p.currencyCode = :currencyCode
              and p.createdAt >= :from
              and p.createdAt < :to
              and p.status in (com.x.payment.entity.PaymentStatus.PAID,
                               com.x.payment.entity.PaymentStatus.PARTIALLY_REFUNDED,
                               com.x.payment.entity.PaymentStatus.REFUNDED)
            group by p.method, p.provider
            order by p.method, p.provider
            """)
    List<PaymentBreakdownProjection> paymentBreakdown(
            @Param("storeId") Long storeId,
            @Param("currencyCode") String currencyCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Payments per hour (pattern %Y-%m-%d %H:00), day (%Y-%m-%d) or month (%Y-%m) of the viewer's zone, which is
     * offsetMinutes ahead of the stored UTC times. Refunds count in the period of the payment they reverse.
     */
    @Query(value = """
            select date_format(date_add(p.created_at, interval :offsetMinutes minute), :pattern) as period,
                   count(p.id) as paymentCount,
                   coalesce(sum(p.amount), 0) as totalAmount,
                   coalesce(sum(p.refunded_amount), 0) as refundedAmount
              from payments p
             where p.store_id = :storeId
               and p.currency_code = :currencyCode
               and p.created_at >= :from
               and p.created_at < :to
               and p.status in ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
             group by period
             order by period
            """, nativeQuery = true)
    List<PaymentPeriodProjection> paymentPeriods(
            @Param("storeId") Long storeId,
            @Param("currencyCode") String currencyCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offsetMinutes") int offsetMinutes,
            @Param("pattern") String pattern);
}
