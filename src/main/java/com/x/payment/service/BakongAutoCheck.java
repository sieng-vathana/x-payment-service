package com.x.payment.service;

import com.x.payment.entity.Payment;
import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;
import com.x.payment.entity.PaymentStatus;
import com.x.payment.realtime.PaymentRealtime;
import com.x.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakong cannot call us when a KHQR is paid; it can only be asked. Instead of every POS asking every 1.5
 * seconds, the server asks for all open KHQRs and the POS is told through the realtime service when one is paid.
 */
@Component
public class BakongAutoCheck {

    private static final Logger log = LoggerFactory.getLogger(BakongAutoCheck.class);
    /** A KHQR is valid for 10 minutes; one more minute covers a payment made just before it expired. */
    static final Duration OPEN_FOR = Duration.ofMinutes(11);
    private static final Duration FAILURE_NOTICE_EVERY = Duration.ofSeconds(30);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentRealtime paymentRealtime;
    private final Map<Long, Instant> lastFailureNotice = new ConcurrentHashMap<>();

    public BakongAutoCheck(PaymentRepository paymentRepository, PaymentService paymentService, PaymentRealtime paymentRealtime) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.paymentRealtime = paymentRealtime;
    }

    @Scheduled(fixedDelay = 1500, initialDelay = 10_000)
    public void checkOpenKhqrs() {
        for (Payment payment : paymentRepository.findByProviderAndMethodAndStatusAndCreatedAtAfter(
                PaymentProvider.BAKONG, PaymentMethod.QR, PaymentStatus.PENDING, LocalDateTime.now().minus(OPEN_FOR))) {
            check(payment);
        }
        lastFailureNotice.keySet().removeIf(id -> lastFailureNotice.get(id).isBefore(Instant.now().minus(OPEN_FOR)));
    }

    void check(Payment payment) {
        try {
            paymentService.checkBakong(payment.getId());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.SERVICE_UNAVAILABLE.value()) {
                log.warn("Bakong check of payment {} failed: {}", payment.getId(), exception.getReason());
                return;
            }
            Instant last = lastFailureNotice.get(payment.getId());
            if (last == null || last.isBefore(Instant.now().minus(FAILURE_NOTICE_EVERY))) {
                lastFailureNotice.put(payment.getId(), Instant.now());
                paymentRealtime.checkFailed(payment.getBusinessId(), payment.getId(), exception.getReason());
            }
        } catch (RuntimeException exception) {
            // Another check marked it paid at the same moment, or the database hiccupped; the next round sees it.
            log.debug("Bakong check of payment {} skipped: {}", payment.getId(), exception.getMessage());
        }
    }
}
