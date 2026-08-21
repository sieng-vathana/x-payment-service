package com.x.payment.service;

import com.x.payment.dto.CashMovementRequest;
import com.x.payment.dto.CashSessionResponse;
import com.x.payment.dto.CloseCashSessionRequest;
import com.x.payment.dto.OpenCashSessionRequest;
import com.x.payment.entity.CashMovement;
import com.x.payment.entity.CashMovementType;
import com.x.payment.entity.CashSession;
import com.x.payment.entity.CashSessionStatus;
import com.x.payment.repository.CashMovementRepository;
import com.x.payment.repository.CashPaymentTotalsProjection;
import com.x.payment.repository.CashSessionRepository;
import com.x.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashSessionServiceTest {
    @Mock
    private CashSessionRepository cashSessionRepository;
    @Mock
    private CashMovementRepository cashMovementRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @InjectMocks
    private CashSessionService cashSessionService;

    @Test
    void opensRegisterWithNormalizedCurrencyAndOpeningBalance() {
        when(cashSessionRepository.findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
                3L, 7L, "USD", CashSessionStatus.OPEN)).thenReturn(Optional.empty());
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> {
            CashSession session = invocation.getArgument(0);
            session.setId(11L);
            return session;
        });
        when(cashMovementRepository.findBySessionIdOrderByCreatedAtAsc(11L)).thenReturn(List.of());
        CashPaymentTotalsProjection totals = paymentTotals("0.00", "0.00", 0L);
        when(paymentRepository.cashPaymentTotals(eq(3L), eq(7L), eq("USD"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals);

        CashSessionResponse response = cashSessionService.open(new OpenCashSessionRequest(
                2L, 3L, 7L, " usd ", new BigDecimal("100"), "Opening float"));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(CashSessionStatus.OPEN);
        assertThat(response.openingFloat()).isEqualByComparingTo("100.00");
        assertThat(response.expectedCash()).isEqualByComparingTo("100.00");
        assertThat(response.note()).isEqualTo("Opening float");
    }

    @Test
    void rejectsOpeningASecondRegisterForTheSameCashierAndCurrency() {
        CashSession existing = CashSession.builder().id(11L).build();
        when(cashSessionRepository.findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
                3L, 7L, "USD", CashSessionStatus.OPEN)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cashSessionService.open(new OpenCashSessionRequest(
                2L, 3L, 7L, "USD", new BigDecimal("100"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("already has an open cash register");
    }

    @Test
    void recordsMovementAndClosesWithExpectedCashAndVariance() {
        LocalDateTime openedAt = LocalDateTime.now().minusHours(2);
        CashSession session = CashSession.builder()
                .id(11L)
                .businessId(2L)
                .storeId(3L)
                .cashierId(7L)
                .currencyCode("USD")
                .status(CashSessionStatus.OPEN)
                .openingFloat(new BigDecimal("100.00"))
                .expectedCash(new BigDecimal("100.00"))
                .openedAt(openedAt)
                .openedBy(7L)
                .build();
        CashMovement payIn = CashMovement.builder()
                .id(21L)
                .sessionId(11L)
                .type(CashMovementType.PAY_IN)
                .amount(new BigDecimal("20.00"))
                .reason("Float top-up")
                .createdBy(7L)
                .createdAt(openedAt.plusMinutes(10))
                .build();
        CashMovement payOut = CashMovement.builder()
                .id(22L)
                .sessionId(11L)
                .type(CashMovementType.PAY_OUT)
                .amount(new BigDecimal("7.00"))
                .reason("Petty cash")
                .createdBy(7L)
                .createdAt(openedAt.plusMinutes(20))
                .build();
        CashPaymentTotalsProjection totals = paymentTotals("50.00", "5.00", 2L);

        when(cashSessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashMovementRepository.save(any(CashMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashMovementRepository.findBySessionIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(payIn, payOut));
        when(paymentRepository.cashPaymentTotals(eq(3L), eq(7L), eq("USD"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals);

        CashSessionResponse movementResponse = cashSessionService.addMovement(11L, new CashMovementRequest(
                CashMovementType.PAY_OUT, new BigDecimal("7.00"), "Petty cash", 7L));
        CashSessionResponse closed = cashSessionService.close(11L, new CloseCashSessionRequest(
                new BigDecimal("160.00"), 7L, "Drawer counted"));

        assertThat(movementResponse.expectedCash()).isEqualByComparingTo("158.00");
        assertThat(movementResponse.cashSales()).isEqualByComparingTo("50.00");
        assertThat(movementResponse.cashRefunds()).isEqualByComparingTo("5.00");
        assertThat(movementResponse.cashIn()).isEqualByComparingTo("20.00");
        assertThat(movementResponse.cashOut()).isEqualByComparingTo("7.00");
        assertThat(closed.status()).isEqualTo(CashSessionStatus.CLOSED);
        assertThat(closed.expectedCash()).isEqualByComparingTo("158.00");
        assertThat(closed.countedCash()).isEqualByComparingTo("160.00");
        assertThat(closed.variance()).isEqualByComparingTo("2.00");
        assertThat(closed.closeNote()).isEqualTo("Drawer counted");
    }

    private CashPaymentTotalsProjection paymentTotals(String gross, String refunded, long count) {
        CashPaymentTotalsProjection projection = mock(CashPaymentTotalsProjection.class);
        when(projection.getGrossAmount()).thenReturn(new BigDecimal(gross));
        when(projection.getRefundedAmount()).thenReturn(new BigDecimal(refunded));
        when(projection.getPaymentCount()).thenReturn(count);
        return projection;
    }
}
