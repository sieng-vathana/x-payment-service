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
 * Bakong cannot call us when a KHQR is paid; it can only be asked, and it allows only about 100 checks a day for
 * this token. So each open KHQR is checked on a gentle schedule (every 3 seconds for the first minute, then every
 * 10), and when Bakong reports the daily limit, checks pause for an hour. The POS is told about each result through
 * the realtime service, and it can still confirm a payment by hand.
 */
@Component
public class BakongAutoCheck {

    private static final Logger log = LoggerFactory.getLogger(BakongAutoCheck.class);
    /** A KHQR is valid for 10 minutes; one more minute covers a payment made just before it expired. */
    static final Duration OPEN_FOR = Duration.ofMinutes(11);
    static final Duration FAST_FOR = Duration.ofMinutes(1);
    static final Duration FAST_EVERY = Duration.ofSeconds(3);
    static final Duration SLOW_EVERY = Duration.ofSeconds(10);
    static final Duration LIMIT_PAUSE = Duration.ofHours(1);
    private static final Duration FAILURE_NOTICE_EVERY = Duration.ofSeconds(30);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentRealtime paymentRealtime;
    private final Map<Long, Instant> lastFailureNotice = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastChecked = new ConcurrentHashMap<>();
    private volatile Instant bakongPausedUntil = Instant.MIN;

    public BakongAutoCheck(PaymentRepository paymentRepository, PaymentService paymentService, PaymentRealtime paymentRealtime) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.paymentRealtime = paymentRealtime;
    }

    @Scheduled(fixedDelay = 1500, initialDelay = 10_000)
    public void checkOpenKhqrs() {
        Instant now = Instant.now();
        if (now.isBefore(bakongPausedUntil)) {
            return;
        }
        for (Payment payment : paymentRepository.findByProviderAndMethodAndStatusAndCreatedAtAfter(
                PaymentProvider.BAKONG, PaymentMethod.QR, PaymentStatus.PENDING, LocalDateTime.now().minus(OPEN_FOR))) {
            Instant last = lastChecked.get(payment.getId());
            boolean firstMinute = payment.getCreatedAt() != null
                    && Duration.between(payment.getCreatedAt(), LocalDateTime.now()).compareTo(FAST_FOR) < 0;
            Duration every = firstMinute ? FAST_EVERY : SLOW_EVERY;
            if (last != null && Duration.between(last, now).compareTo(every) < 0) {
                continue;
            }
            lastChecked.put(payment.getId(), now);
            check(payment);
            if (now.isBefore(bakongPausedUntil)) {
                break;
            }
        }
        Instant cutoff = now.minus(OPEN_FOR);
        lastFailureNotice.keySet().removeIf(id -> lastFailureNotice.get(id).isBefore(cutoff));
        lastChecked.keySet().removeIf(id -> lastChecked.get(id).isBefore(cutoff));
    }

    void check(Payment payment) {
        try {
            paymentService.checkBakong(payment.getId());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != HttpStatus.SERVICE_UNAVAILABLE.value()) {
                log.warn("Bakong check of payment {} failed: {}", payment.getId(), exception.getReason());
                return;
            }
            String reason = exception.getReason() == null ? "Bakong could not be reached." : exception.getReason();
            if (reason.toLowerCase().contains("daily request limit")) {
                bakongPausedUntil = Instant.now().plus(LIMIT_PAUSE);
                log.warn("Bakong daily check limit reached; automatic checks paused until {}", bakongPausedUntil);
            }
            Instant last = lastFailureNotice.get(payment.getId());
            if (last == null || last.isBefore(Instant.now().minus(FAILURE_NOTICE_EVERY))) {
                lastFailureNotice.put(payment.getId(), Instant.now());
                paymentRealtime.checkFailed(payment.getBusinessId(), payment.getId(), reason);
            }
        } catch (RuntimeException exception) {
            // Another check marked it paid at the same moment, or the database hiccupped; the next round sees it.
            log.debug("Bakong check of payment {} skipped: {}", payment.getId(), exception.getMessage());
        }
    }
}
