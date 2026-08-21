package com.x.payment.service;

import com.x.payment.dto.CashMovementRequest;
import com.x.payment.dto.CashMovementResponse;
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
public class CashSessionService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public CashSessionResponse current(Long storeId, Long cashierId, String currencyCode) {
        String currency = normalizeCurrency(currencyCode);
        return cashSessionRepository
                .findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
                        storeId, cashierId, currency, CashSessionStatus.OPEN)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CashSessionResponse> history(Long storeId, Long cashierId, String currencyCode) {
        String currency = normalizeCurrency(currencyCode);
        return cashSessionRepository
                .findTop20ByStoreIdAndCashierIdAndCurrencyCodeOrderByOpenedAtDesc(storeId, cashierId, currency)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CashSessionResponse currentById(Long id) {
        return cashSessionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash session not found"));
    }

    @Transactional
    public CashSessionResponse open(OpenCashSessionRequest request) {
        String currency = normalizeCurrency(request.currencyCode());
        if (cashSessionRepository
                .findFirstByStoreIdAndCashierIdAndCurrencyCodeAndStatusOrderByOpenedAtDesc(
                        request.storeId(), request.cashierId(), currency, CashSessionStatus.OPEN)
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This cashier already has an open cash register");
        }

        LocalDateTime now = LocalDateTime.now();
        CashSession session = CashSession.builder()
                .businessId(request.businessId())
                .storeId(request.storeId())
                .cashierId(request.cashierId())
                .currencyCode(currency)
                .status(CashSessionStatus.OPEN)
                .openingFloat(money(request.openingFloat()))
                .expectedCash(money(request.openingFloat()))
                .openedAt(now)
                .openedBy(request.cashierId())
                .note(trim(request.note()))
                .build();
        return toResponse(cashSessionRepository.save(session));
    }

    @Transactional
    public CashSessionResponse addMovement(Long id, CashMovementRequest request) {
        CashSession session = requireOpen(id);
        CashMovement movement = CashMovement.builder()
                .sessionId(session.getId())
                .type(request.type())
                .amount(money(request.amount()))
                .reason(request.reason().trim())
                .createdBy(request.createdBy())
                .build();
        cashMovementRepository.save(movement);
        return toResponse(session);
    }

    @Transactional
    public CashSessionResponse close(Long id, CloseCashSessionRequest request) {
        CashSession session = requireOpen(id);
        LocalDateTime now = LocalDateTime.now();
        BigDecimal expected = calculateExpectedCash(session, now);
        BigDecimal counted = money(request.countedCash());
        session.setExpectedCash(expected);
        session.setCountedCash(counted);
        session.setVariance(money(counted.subtract(expected)));
        session.setClosedAt(now);
        session.setClosedBy(request.closedBy());
        session.setCloseNote(trim(request.closeNote()));
        session.setStatus(CashSessionStatus.CLOSED);
        return toResponse(cashSessionRepository.save(session));
    }

    private CashSession requireOpen(Long id) {
        CashSession session = cashSessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash session not found"));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cash session is already closed");
        }
        return session;
    }

    private CashSessionResponse toResponse(CashSession session) {
        LocalDateTime end = session.getClosedAt() == null ? LocalDateTime.now() : session.getClosedAt();
        CashSummary summary = calculateSummary(session, end);
        BigDecimal expected = session.getStatus() == CashSessionStatus.CLOSED && session.getExpectedCash() != null
                ? session.getExpectedCash() : summary.expectedCash();
        return new CashSessionResponse(
                session.getId(), session.getBusinessId(), session.getStoreId(), session.getCashierId(),
                session.getCurrencyCode(), session.getStatus(), session.getOpeningFloat(),
                summary.cashSales(), summary.cashRefunds(), summary.cashIn(), summary.cashOut(),
                summary.paymentCount(), expected, session.getCountedCash(), session.getVariance(),
                session.getOpenedAt(), session.getClosedAt(), session.getOpenedBy(), session.getClosedBy(),
                session.getNote(), session.getCloseNote(),
                cashMovementRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(CashMovementResponse::from).toList());
    }

    private BigDecimal calculateExpectedCash(CashSession session, LocalDateTime end) {
        return calculateSummary(session, end).expectedCash();
    }

    private CashSummary calculateSummary(CashSession session, LocalDateTime end) {
        CashPaymentTotalsProjection paymentTotals = paymentRepository.cashPaymentTotals(
                session.getStoreId(), session.getCashierId(), session.getCurrencyCode(), session.getOpenedAt(), end);
        BigDecimal cashSales = paymentTotals == null || paymentTotals.getGrossAmount() == null
                ? ZERO : money(paymentTotals.getGrossAmount());
        BigDecimal cashRefunds = paymentTotals == null || paymentTotals.getRefundedAmount() == null
                ? ZERO : money(paymentTotals.getRefundedAmount());
        long paymentCount = paymentTotals == null ? 0 : paymentTotals.getPaymentCount();

        BigDecimal cashIn = ZERO;
        BigDecimal cashOut = ZERO;
        for (CashMovement movement : cashMovementRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())) {
            if (movement.getType() == CashMovementType.PAY_IN) {
                cashIn = cashIn.add(movement.getAmount());
            } else {
                cashOut = cashOut.add(movement.getAmount());
            }
        }
        BigDecimal expected = session.getOpeningFloat()
                .add(cashSales)
                .subtract(cashRefunds)
                .add(cashIn)
                .subtract(cashOut);
        return new CashSummary(
                money(cashSales), money(cashRefunds), money(cashIn), money(cashOut), paymentCount, money(expected));
    }

    private record CashSummary(
            BigDecimal cashSales,
            BigDecimal cashRefunds,
            BigDecimal cashIn,
            BigDecimal cashOut,
            long paymentCount,
            BigDecimal expectedCash) {
    }

    private String normalizeCurrency(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code is required");
        }
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
