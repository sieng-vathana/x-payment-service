package com.x.payment.service;

import com.x.payment.dto.OfflineQueueReport;
import com.x.payment.entity.PosOfflineQueue;
import com.x.payment.repository.PosOfflineQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OfflineQueueService {

    private static final int MAX_DEVICE_ID = 64;

    private final PosOfflineQueueRepository repository;

    @Transactional
    public PosOfflineQueue report(String deviceId, OfflineQueueReport report) {
        String id = deviceId == null ? "" : deviceId.trim();
        if (id.isEmpty() || id.length() > MAX_DEVICE_ID) {
            throw new IllegalArgumentException("device id is required");
        }
        return repository.save(report.toEntity(id, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<PosOfflineQueue> forBusiness(Long businessId) {
        return businessId == null
                ? repository.findAllByOrderByReportedAtDesc()
                : repository.findByBusinessIdOrderByReportedAtDesc(businessId);
    }
}
