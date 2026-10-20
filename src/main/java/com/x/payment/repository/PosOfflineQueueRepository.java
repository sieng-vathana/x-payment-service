package com.x.payment.repository;

import com.x.payment.entity.PosOfflineQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosOfflineQueueRepository extends JpaRepository<PosOfflineQueue, String> {

    List<PosOfflineQueue> findByBusinessIdOrderByReportedAtDesc(Long businessId);

    List<PosOfflineQueue> findAllByOrderByReportedAtDesc();
}
