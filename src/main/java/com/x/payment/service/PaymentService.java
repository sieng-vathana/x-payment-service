package com.x.payment.service;

import com.x.payment.dto.*;
import com.x.payment.entity.*;
import com.x.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(PaymentResponse::from)
                .orElseGet(() -> createNew(request));
    }

    private PaymentResponse createNew(CreatePaymentRequest request) {
        validateMethodAndProvider(request);
        String currency = normalizeCurrency(request.currencyCode());
        BigDecimal amount = money(request.amount());
        boolean cash = request.method() == PaymentMethod.CASH;
        BigDecimal tendered = request.tenderedAmount() == null ? null : money(request.tenderedAmount());
        if (cash && (tendered == null || tendered.compareTo(amount) < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cash tenderedAmount must be greater than or equal to amount");
        }
        Payment payment = Payment.builder()
                .orderId(request.orderId()).businessId(request.businessId()).storeId(request.storeId())
                .amount(amount).tenderedAmount(tendered)
                .changeAmount(cash ? tendered.subtract(amount) : ZERO)
                .refundedAmount(ZERO).currencyCode(currency).method(request.method()).provider(request.provider())
                .status(cash ? PaymentStatus.PAID : PaymentStatus.PENDING)
                .externalReference(trim(request.externalReference())).idempotencyKey(request.idempotencyKey().trim())
                .note(trim(request.note())).paidAt(cash ? LocalDateTime.now() : null).build();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse confirm(Long id, ConfirmPaymentRequest request) {
        Payment payment = find(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending payment can be confirmed");
        }
        payment.setExternalReference(request.externalReference().trim());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse refund(Long id, RefundPaymentRequest request) {
        Payment payment = find(id);
        if (payment.getStatus() != PaymentStatus.PAID
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a paid payment can be refunded");
        }
        BigDecimal newRefunded = payment.getRefundedAmount().add(money(request.amount()));
        if (newRefunded.compareTo(payment.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund exceeds paid amount");
        }
        payment.setRefundedAmount(newRefunded);
        payment.setStatus(newRefunded.compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        payment.setNote(trim(request.reason()));
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentBreakdownResponse> breakdown(Long storeId, LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        return paymentRepository.paymentBreakdown(storeId, from, to).stream()
                .map(row -> new PaymentBreakdownResponse(
                        row.getMethod(), row.getProvider(), row.getPaymentCount(),
                        row.getTotalAmount(), row.getRefundedAmount()))
                .toList();
    }

    private Payment find(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private void validateMethodAndProvider(CreatePaymentRequest request) {
        if (request.method() == PaymentMethod.CASH && request.provider() != PaymentProvider.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash payment provider must be NONE");
        }
        if (request.method() != PaymentMethod.CASH && request.provider() == PaymentProvider.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR and card payments require a provider");
        }
    }

    private String normalizeCurrency(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 4217 currency code");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
