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

    @Query("""
            select p.method as method, p.provider as provider, count(p.id) as paymentCount,
                   coalesce(sum(p.amount), 0) as totalAmount,
                   coalesce(sum(p.refundedAmount), 0) as refundedAmount
            from Payment p
            where p.storeId = :storeId
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
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
