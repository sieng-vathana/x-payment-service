package com.x.payment.service;

import com.x.payment.dto.*;
import com.x.payment.config.KhqrPayProperties;
import com.x.payment.entity.*;
import com.x.payment.gateway.QrPaymentGateway;
import com.x.payment.gateway.QrPaymentInitiation;
import com.x.payment.gateway.KhqrPaySigner;
import com.x.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final long SIMULATED_PAYMENT_DELAY_SECONDS = 2L;
    private static final int SIMULATED_QR_MODULES = 29;
    private static final int SIMULATED_QR_MODULE_SIZE = 8;
    private final PaymentRepository paymentRepository;
    private final QrPaymentGateway qrPaymentGateway;
    private final KhqrPayProperties khqrPayProperties;
    private final KhqrPaySigner khqrPaySigner = new KhqrPaySigner();

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(PaymentResponse::from)
                .orElseGet(() -> createNew(request));
    }

    @Transactional
    public QrPaymentResponse createQr(CreateQrPaymentRequest request) {
        String idempotencyKey = request.idempotencyKey().trim();
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::reuseQrPayment)
                .orElseGet(() -> createNewQr(request, idempotencyKey));
    }

    @Transactional
    public QrPaymentResponse createSimulatedQr(CreateQrPaymentRequest request) {
        String idempotencyKey = request.idempotencyKey().trim();
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::reuseSimulatedQrPayment)
                .orElseGet(() -> createNewSimulatedQr(request, idempotencyKey));
    }

    private QrPaymentResponse createNewQr(CreateQrPaymentRequest request, String idempotencyKey) {
        String currency = normalizeCurrency(request.currencyCode());
        if (!currency.equals("USD")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This KHQRPay Direct API checkout supports only USD");
        }
        BigDecimal amount = money(request.amount());
        String transactionId = transactionId(request.orderId(), idempotencyKey);
        QrPaymentInitiation initiation = qrPaymentGateway.initiate(
                transactionId, amount, trim(request.note()));
        Payment payment = Payment.builder()
                .orderId(request.orderId()).businessId(request.businessId()).storeId(request.storeId())
                .amount(amount).tenderedAmount(null).changeAmount(ZERO).refundedAmount(ZERO)
                .currencyCode(currency).method(PaymentMethod.QR).provider(PaymentProvider.KHQRPAY)
                .status(PaymentStatus.PENDING).externalReference(initiation.transactionId())
                .idempotencyKey(idempotencyKey).note(trim(request.note())).build();
        return QrPaymentResponse.created(PaymentResponse.from(paymentRepository.save(payment)), initiation);
    }

    private QrPaymentResponse reuseQrPayment(Payment payment) {
        if (payment.getMethod() != PaymentMethod.QR || payment.getProvider() != PaymentProvider.KHQRPAY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Idempotency key already belongs to a different payment");
        }
        QrPaymentInitiation initiation = qrPaymentGateway.initiate(
                payment.getExternalReference(), payment.getAmount(), payment.getNote());
        return QrPaymentResponse.reused(PaymentResponse.from(payment), initiation);
    }

    private QrPaymentResponse createNewSimulatedQr(CreateQrPaymentRequest request, String idempotencyKey) {
        String currency = normalizeCurrency(request.currencyCode());
        BigDecimal amount = money(request.amount());
        String transactionId = simulatedTransactionId(request.orderId(), idempotencyKey);
        QrPaymentInitiation initiation = simulatedQr(transactionId, amount);
        Payment payment = Payment.builder()
                .orderId(request.orderId()).businessId(request.businessId()).storeId(request.storeId())
                .amount(amount).tenderedAmount(null).changeAmount(ZERO).refundedAmount(ZERO)
                .currencyCode(currency).method(PaymentMethod.QR).provider(PaymentProvider.SIMULATED)
                .status(PaymentStatus.PENDING).externalReference(transactionId)
                .idempotencyKey(idempotencyKey).note(trim(request.note())).build();
        return QrPaymentResponse.created(PaymentResponse.from(paymentRepository.save(payment)), initiation);
    }

    private QrPaymentResponse reuseSimulatedQrPayment(Payment payment) {
        if (payment.getMethod() != PaymentMethod.QR || payment.getProvider() != PaymentProvider.SIMULATED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Idempotency key already belongs to a different payment");
        }
        return QrPaymentResponse.reused(
                PaymentResponse.from(payment), simulatedQr(payment.getExternalReference(), payment.getAmount()));
    }

    private PaymentResponse createNew(CreatePaymentRequest request) {
        if (request.method() == PaymentMethod.QR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Create KHQRPay payments with POST /api/v1/payments/qr");
        }
        if (request.method() != PaymentMethod.CASH && request.method() != PaymentMethod.CARD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only cash, card, and KHQRPay QR are supported");
        }
        if (request.method() == PaymentMethod.CASH) validateCashProvider(request);
        if (request.method() == PaymentMethod.CARD && request.provider() != PaymentProvider.OTHER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card payment provider must be OTHER");
        }
        String currency = normalizeCurrency(request.currencyCode());
        BigDecimal amount = money(request.amount());
        BigDecimal tendered = request.method() == PaymentMethod.CARD
                ? amount
                : request.tenderedAmount() == null ? null : money(request.tenderedAmount());
        if (tendered == null || tendered.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cash tenderedAmount must be greater than or equal to amount");
        }
        Payment payment = Payment.builder()
                .orderId(request.orderId()).businessId(request.businessId()).storeId(request.storeId())
                .amount(amount).tenderedAmount(tendered)
                .changeAmount(tendered.subtract(amount))
                .refundedAmount(ZERO).currencyCode(currency).method(request.method()).provider(request.provider())
                .status(PaymentStatus.PAID)
                .externalReference(trim(request.externalReference())).idempotencyKey(request.idempotencyKey().trim())
                .note(trim(request.note())).paidAt(LocalDateTime.now()).build();
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(find(id));
    }

    @Transactional
    public PaymentResponse simulateCallback(Long id) {
        Payment payment = find(id);
        if (payment.getProvider() != PaymentProvider.SIMULATED || payment.getMethod() != PaymentMethod.QR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only simulated QR payments can use the simulated callback");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending simulated payment can be completed");
        }
        if (payment.getCreatedAt() == null
                || payment.getCreatedAt().plusSeconds(SIMULATED_PAYMENT_DELAY_SECONDS).isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Simulated payment is not ready yet");
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse confirm(Long id, ConfirmPaymentRequest request) {
        Payment payment = find(id);
        if (payment.getProvider() == PaymentProvider.KHQRPAY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "KHQRPay payments must be confirmed by provider verification");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending payment can be confirmed");
        }
        payment.setExternalReference(request.externalReference().trim());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse handleKhqrPayWebhook(KhqrPayWebhookRequest request) {
        String transactionId = requiredWebhookValue(
                request.transactionReference(), "transaction_id");
        String amountText = requiredWebhookValue(request.amountText(), "amount");
        String status = requiredWebhookValue(request.status(), "status");

        if (!khqrPaySigner.verifyWebhook(
                requiredWebhookValue(khqrPayProperties.getMerchantSecret(), "merchant secret"),
                transactionId,
                amountText,
                status,
                request.requestTime(),
                request.hash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid KHQRPay webhook hash");
        }

        Payment payment = paymentRepository.findByExternalReference(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "KHQRPay transaction not found"));
        if (payment.getProvider() != PaymentProvider.KHQRPAY || payment.getMethod() != PaymentMethod.QR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is not a KHQRPay QR payment");
        }

        BigDecimal paidAmount;
        try {
            paidAmount = new BigDecimal(amountText);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid KHQRPay amount");
        }
        if (paidAmount.compareTo(payment.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KHQRPay amount does not match the payment");
        }

        if (!isSuccessfulKhqrPayStatus(status) || payment.getStatus() == PaymentStatus.PAID) {
            return PaymentResponse.from(payment);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse refund(Long id, RefundPaymentRequest request) {
        Payment payment = find(id);
        if (payment.getProvider() == PaymentProvider.KHQRPAY) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "KHQRPay refunds must be completed through the receiving bank or provider");
        }
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

    private void validateCashProvider(CreatePaymentRequest request) {
        if (request.provider() != PaymentProvider.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash payment provider must be NONE");
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

    private String transactionId(Long orderId, String idempotencyKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                suffix.append(String.format("%02x", digest[index]));
            }
            return "XP-" + orderId + "-" + suffix;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String simulatedTransactionId(Long orderId, String idempotencyKey) {
        return "SIM-" + transactionId(orderId, idempotencyKey).substring(3);
    }

    private QrPaymentInitiation simulatedQr(String transactionId, BigDecimal amount) {
        String payload = "SIMULATED-KHQR|" + transactionId + "|" + amount.toPlainString();
        String expiresAt = LocalDateTime.now().plus(Duration.ofMinutes(5)).toString();
        return new QrPaymentInitiation(
                transactionId,
                payload,
                simulatedQrImage(transactionId),
                null,
                expiresAt);
    }

    private String simulatedQrImage(String seed) {
        boolean[][] modules = new boolean[SIMULATED_QR_MODULES][SIMULATED_QR_MODULES];
        addFinderPattern(modules, 0, 0);
        addFinderPattern(modules, SIMULATED_QR_MODULES - 7, 0);
        addFinderPattern(modules, 0, SIMULATED_QR_MODULES - 7);

        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (int row = 0; row < SIMULATED_QR_MODULES; row++) {
            for (int column = 0; column < SIMULATED_QR_MODULES; column++) {
                if (!modules[row][column]) {
                    int bit = (row * SIMULATED_QR_MODULES + column) % (digest.length * 8);
                    modules[row][column] = ((digest[bit / 8] >>> (bit % 8)) & 1) == 1;
                }
            }
        }

        int size = SIMULATED_QR_MODULES * SIMULATED_QR_MODULE_SIZE;
        StringBuilder svg = new StringBuilder()
                .append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(size).append(' ').append(size).append("\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
        for (int row = 0; row < SIMULATED_QR_MODULES; row++) {
            for (int column = 0; column < SIMULATED_QR_MODULES; column++) {
                if (modules[row][column]) {
                    svg.append("<rect x=\"").append(column * SIMULATED_QR_MODULE_SIZE)
                            .append("\" y=\"").append(row * SIMULATED_QR_MODULE_SIZE)
                            .append("\" width=\"").append(SIMULATED_QR_MODULE_SIZE)
                            .append("\" height=\"").append(SIMULATED_QR_MODULE_SIZE)
                            .append("\" fill=\"#111827\"/>");
                }
            }
        }
        svg.append("</svg>");
        return "data:image/svg+xml;base64," + Base64.getEncoder()
                .encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void addFinderPattern(boolean[][] modules, int left, int top) {
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 7; column++) {
                boolean border = row == 0 || row == 6 || column == 0 || column == 6;
                boolean center = row >= 2 && row <= 4 && column >= 2 && column <= 4;
                modules[top + row][left + column] = border || center;
            }
        }
    }

    private boolean isSuccessfulKhqrPayStatus(String status) {
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PAID", "SUCCESS", "SUCCEEDED", "COMPLETED", "CAPTURED" -> true;
            default -> false;
        };
    }

    private String requiredWebhookValue(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KHQRPay webhook is missing " + field);
        }
        return value.trim();
    }
}
