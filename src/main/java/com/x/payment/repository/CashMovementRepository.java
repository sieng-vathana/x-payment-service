package com.x.payment.repository;

import com.x.payment.entity.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    List<CashMovement> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
