package com.x.payment.controller;

import com.sharedlib.response.ApiResponse;
import com.x.payment.dto.OfflineQueueReport;
import com.x.payment.entity.PosOfflineQueue;
import com.x.payment.service.OfflineQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cash sales held offline on POS devices, as last reported by each device. */
@RestController
@RequestMapping("/api/v1/payments/offline-queue")
@RequiredArgsConstructor
public class OfflineQueueController {

    private final OfflineQueueService service;

    @PutMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<PosOfflineQueue>> report(
            @PathVariable String deviceId, @Valid @RequestBody OfflineQueueReport report) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Offline queue saved", service.report(deviceId, report)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PosOfflineQueue>>> list(@RequestParam(required = false) Long businessId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), service.forBusiness(businessId)));
    }
}
