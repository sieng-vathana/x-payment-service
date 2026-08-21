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
    private final com.x.payment.service.CashSessionService cashSessionService;

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

    @PostMapping("/simulated-qr")
    public ResponseEntity<ApiResponse<QrPaymentResponse>> createSimulatedQr(
            @Valid @RequestBody CreateQrPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Simulated QR payment created", paymentService.createSimulatedQr(request)));
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

    @PostMapping("/khqrpay/webhook")
    public ResponseEntity<ApiResponse<PaymentResponse>> khqrPayWebhook(
            @RequestBody KhqrPayWebhookRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                200, "KHQRPay webhook processed", paymentService.handleKhqrPayWebhook(request)));
    }

    @PostMapping("/{id}/simulate-callback")
    public ResponseEntity<ApiResponse<PaymentResponse>> simulateCallback(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                200, "Simulated payment callback processed", paymentService.simulateCallback(id)));
    }

    @GetMapping("/reports/breakdown")
    public ResponseEntity<ApiResponse<List<PaymentBreakdownResponse>>> breakdown(
            @RequestParam @Positive Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(200, paymentService.breakdown(storeId, from, to)));
    }

    @GetMapping("/cash-sessions/current")
    public ResponseEntity<ApiResponse<CashSessionResponse>> currentCashSession(
            @RequestParam @Positive Long storeId,
            @RequestParam @Positive Long cashierId,
            @RequestParam String currencyCode) {
        return ResponseEntity.ok(ApiResponse.success(200,
                cashSessionService.current(storeId, cashierId, currencyCode)));
    }

    @GetMapping("/cash-sessions/history")
    public ResponseEntity<ApiResponse<List<CashSessionResponse>>> cashSessionHistory(
            @RequestParam @Positive Long storeId,
            @RequestParam @Positive Long cashierId,
            @RequestParam String currencyCode) {
        return ResponseEntity.ok(ApiResponse.success(200,
                cashSessionService.history(storeId, cashierId, currencyCode)));
    }

    @PostMapping("/cash-sessions/open")
    public ResponseEntity<ApiResponse<CashSessionResponse>> openCashSession(
            @Valid @RequestBody OpenCashSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Cash register opened", cashSessionService.open(request)));
    }

    @GetMapping("/cash-sessions/{id}")
    public ResponseEntity<ApiResponse<CashSessionResponse>> getCashSession(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(200, cashSessionService.currentById(id)));
    }

    @PostMapping("/cash-sessions/{id}/movements")
    public ResponseEntity<ApiResponse<CashSessionResponse>> addCashMovement(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CashMovementRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, "Cash movement recorded",
                cashSessionService.addMovement(id, request)));
    }

    @PostMapping("/cash-sessions/{id}/close")
    public ResponseEntity<ApiResponse<CashSessionResponse>> closeCashSession(
            @PathVariable @Positive Long id,
            @Valid @RequestBody CloseCashSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, "Cash register closed",
                cashSessionService.close(id, request)));
    }
}
