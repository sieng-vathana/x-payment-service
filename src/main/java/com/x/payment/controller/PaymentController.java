package com.x.payment.controller;

import com.sharedlib.response.ApiResponse;
import com.x.payment.dto.*;
import com.x.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Payment created", paymentService.create(request)));
    }

    @PostMapping("/qr")
    public ResponseEntity<ApiResponse<QrPaymentResponse>> createQr(
            @Valid @RequestBody CreateQrPaymentRequest request) {
        QrPaymentResponse response = paymentService.createQr(request);
        if (response.reused()) {
            return ResponseEntity.ok(ApiResponse.success(200, "QR payment already exists", response));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "QR payment created", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(200, paymentService.get(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listForOrder(@RequestParam @Positive Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(200, paymentService.listForOrder(orderId)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @PathVariable @Positive Long id, @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, "Payment confirmed", paymentService.confirm(id, request)));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable @Positive Long id, @Valid @RequestBody RefundPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, "Payment refunded", paymentService.refund(id, request)));
    }

    @GetMapping("/reports/breakdown")
    public ResponseEntity<ApiResponse<List<PaymentBreakdownResponse>>> breakdown(
            @RequestParam @Positive Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(200, paymentService.breakdown(storeId, from, to)));
    }
}
