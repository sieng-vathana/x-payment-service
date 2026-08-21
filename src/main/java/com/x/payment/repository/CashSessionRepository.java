package com.x.payment.repository;

import com.x.payment.entity.CashSession;
import com.x.payment.entity.CashSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
    Optional<CashSession> findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
            Long storeId, Long cashierId, String currencyCode, CashSessionStatus status);

    List<CashSession> findTop20ByStoreIdAndCashierIdAndCurrencyCodeOrderByOpenedAtDesc(
            Long storeId, Long cashierId, String currencyCode);
}
