package com.x.payment.repository;

import com.x.payment.entity.CashSession;
import com.x.payment.entity.CashSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    /** Whether this cashier has a register still open that was already open at the given time. */
    boolean existsByStoreIdAndCashierIdAndCurrencyCodeAndStatusAndOpenedAtLessThanEqual(
            Long storeId, Long cashierId, String currencyCode, CashSessionStatus status, LocalDateTime openedAt);
    Optional<CashSession> findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
            Long storeId, Long cashierId, String currencyCode, CashSessionStatus status);

    List<CashSession> findTop20ByStoreIdAndCashierIdAndCurrencyCodeOrderByOpenedAtDesc(
            Long storeId, Long cashierId, String currencyCode);
}
