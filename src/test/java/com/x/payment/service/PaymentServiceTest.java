package com.x.payment.service;

import com.x.payment.dto.ConfirmPaymentRequest;
import com.x.payment.dto.CreatePaymentRequest;
import com.x.payment.dto.CreateQrPaymentRequest;
import com.x.payment.dto.KhqrPayWebhookRequest;
import com.x.payment.dto.PaymentResponse;
import com.x.payment.dto.QrPaymentResponse;
import com.x.payment.config.KhqrPayProperties;
import com.x.payment.entity.Payment;
import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;
import com.x.payment.entity.PaymentStatus;
import com.x.payment.gateway.QrPaymentGateway;
import com.x.payment.gateway.QrPaymentInitiation;
import com.x.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private QrPaymentGateway qrPaymentGateway;
    @Mock
    private KhqrPayProperties khqrPayProperties;
    @InjectMocks
    private PaymentService paymentService;

    @Test
    void recordsCashAsPaidAndCalculatesChange() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                16L, 2L, 3L, new BigDecimal("12.50"), new BigDecimal("20.00"), "USD",
                PaymentMethod.CASH, PaymentProvider.NONE, null, "checkout-16", null);
        when(paymentRepository.findByIdempotencyKey("checkout-16")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.create(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.changeAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void createsPendingKhqrPayPaymentAndReturnsQrData() {
        CreateQrPaymentRequest request = new CreateQrPaymentRequest(
                15L, 2L, 3L, new BigDecimal("12.50"), "usd", "checkout-15", "Counter 1");
        when(paymentRepository.findByIdempotencyKey("checkout-15")).thenReturn(Optional.empty());
        when(qrPaymentGateway.initiate(anyString(), eq(new BigDecimal("12.50")), eq("Counter 1")))
                .thenAnswer(invocation -> new QrPaymentInitiation(
                        invocation.getArgument(0), "khqr-payload", null, null, "2026-08-12T12:00:00Z"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(99L);
            return payment;
        });

        QrPaymentResponse response = paymentService.createQr(request);

        assertThat(response.reused()).isFalse();
        assertThat(response.qrPayload()).isEqualTo("khqr-payload");
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.payment().method()).isEqualTo(PaymentMethod.QR);
        assertThat(response.payment().provider()).isEqualTo(PaymentProvider.KHQRPAY);
        assertThat(response.payment().externalReference()).startsWith("XP-15-");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void createsPendingSimulatedPaymentWithoutCallingProvider() {
        CreateQrPaymentRequest request = new CreateQrPaymentRequest(
                15L, 2L, 3L, new BigDecimal("12.50"), "USD", "simulated-checkout", "Demo payment");
        when(paymentRepository.findByIdempotencyKey("simulated-checkout")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(100L);
            return payment;
        });

        QrPaymentResponse response = paymentService.createSimulatedQr(request);

        assertThat(response.payment().provider()).isEqualTo(PaymentProvider.SIMULATED);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.transactionId()).startsWith("SIM-15-");
        assertThat(response.qrImageUrl()).startsWith("data:image/svg+xml;base64,");
    }

    @Test
    void simulatedCallbackMarksAnOldPendingPaymentAsPaid() {
        Payment payment = Payment.builder()
                .id(100L)
                .method(PaymentMethod.QR)
                .provider(PaymentProvider.SIMULATED)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now().minusSeconds(3))
                .build();
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.simulateCallback(100L);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void simulatedCallbackRejectsPaymentBeforeTheTwoSecondDelay() {
        Payment payment = Payment.builder()
                .id(100L)
                .method(PaymentMethod.QR)
                .provider(PaymentProvider.SIMULATED)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.simulateCallback(100L))
                .hasMessageContaining("not ready yet");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void doesNotSavePendingPaymentWhenKhqrPayRejectsCheckout() {
        CreateQrPaymentRequest request = new CreateQrPaymentRequest(
                15L, 2L, 3L, new BigDecimal("12.50"), "USD", "checkout-rejected", null);
        when(paymentRepository.findByIdempotencyKey("checkout-rejected")).thenReturn(Optional.empty());
        when(qrPaymentGateway.initiate(anyString(), eq(new BigDecimal("12.50")), eq(null)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "KHQRPay rejected checkout: Invalid Security Hash"));

        assertThatThrownBy(() -> paymentService.createQr(request))
                .hasMessageContaining("Invalid Security Hash");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void doesNotAllowManualConfirmationForKhqrPay() {
        Payment payment = Payment.builder()
                .id(99L)
                .method(PaymentMethod.QR)
                .provider(PaymentProvider.KHQRPAY)
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirm(99L, new ConfirmPaymentRequest("browser-success")))
                .hasMessageContaining("provider verification");
    }

    @Test
    void marksMatchingKhqrPayWebhookAsPaid() {
        Payment payment = Payment.builder()
                .id(99L)
                .orderId(15L)
                .amount(new BigDecimal("12.50"))
                .method(PaymentMethod.QR)
                .provider(PaymentProvider.KHQRPAY)
                .status(PaymentStatus.PENDING)
                .externalReference("XP-15-reference")
                .build();
        when(khqrPayProperties.getMerchantSecret()).thenReturn("secret");
        when(paymentRepository.findByExternalReference("XP-15-reference")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse response = paymentService.handleKhqrPayWebhook(new KhqrPayWebhookRequest(
                null, "XP-15-reference", null, "12.50", "PAID", "20260812203000",
                "544ee85147bd785392fd9c560b97c28c45f38817"));

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void rejectsKhqrPayWebhookWithWrongHashBeforeLookingUpPayment() {
        when(khqrPayProperties.getMerchantSecret()).thenReturn("secret");

        assertThatThrownBy(() -> paymentService.handleKhqrPayWebhook(new KhqrPayWebhookRequest(
                "XP-15-reference", null, "12.50", null, "PAID", "20260812203000", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid KHQRPay webhook hash");
        verify(paymentRepository, never()).findByExternalReference(anyString());
    }

    @Test
    void rebuildsCheckoutUrlForAnIdempotentQrRetry() {
        Payment payment = Payment.builder()
                .id(99L)
                .orderId(15L)
                .businessId(2L)
                .storeId(3L)
                .amount(new BigDecimal("12.50"))
                .changeAmount(new BigDecimal("0.00"))
                .refundedAmount(new BigDecimal("0.00"))
                .currencyCode("USD")
                .method(PaymentMethod.QR)
                .provider(PaymentProvider.KHQRPAY)
                .status(PaymentStatus.PENDING)
                .externalReference("XP-15-reference")
                .idempotencyKey("checkout-15")
                .note("Counter 1")
                .build();
        when(paymentRepository.findByIdempotencyKey("checkout-15")).thenReturn(Optional.of(payment));
        when(qrPaymentGateway.initiate("XP-15-reference", new BigDecimal("12.50"), "Counter 1"))
                .thenReturn(new QrPaymentInitiation(
                        "XP-15-reference", null, null, "https://khqr.cc/checkout", null));

        QrPaymentResponse response = paymentService.createQr(new CreateQrPaymentRequest(
                15L, 2L, 3L, new BigDecimal("12.50"), "USD", "checkout-15", "Counter 1"));

        assertThat(response.reused()).isTrue();
        assertThat(response.checkoutUrl()).isEqualTo("https://khqr.cc/checkout");
    }
}
