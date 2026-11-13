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
    static final Duration QR_LIFETIME = Duration.ofMinutes(10);
    /**
     * When each KHQR is checked, measured from when it was shown: 8 checks per code instead of about 80, so the
     * 100 daily checks cover about a dozen KHQRs. Most customers pay within the first two minutes.
     */
    static final Duration[] CHECK_AT = {
            Duration.ofSeconds(20), Duration.ofSeconds(40), Duration.ofSeconds(70), Duration.ofSeconds(110),
            Duration.ofMinutes(3), Duration.ofMinutes(5), Duration.ofMinutes(8), Duration.ofSeconds(630)};
    static final Duration LIMIT_PAUSE = Duration.ofHours(1);
    private static final Duration FAILURE_NOTICE_EVERY = Duration.ofSeconds(30);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentRealtime paymentRealtime;
    private final Map<Long, Instant> lastFailureNotice = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastChecked = new ConcurrentHashMap<>();
    /** How many scheduled checks each KHQR (payment id + MD5) has had. */
    private final Map<String, Integer> checksDone = new ConcurrentHashMap<>();
    private volatile Instant bakongPausedUntil = Instant.MIN;
    /**
     * While checks are paused, new KHQRs can be paid and then fall out of the 11-minute window unseen. After a pause,
     * one round also covers every KHQR created since shortly before the pause began.
     */
    private volatile LocalDateTime catchUpFrom;

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
        LocalDateTime openSince = LocalDateTime.now().minus(OPEN_FOR);
        LocalDateTime catchUp = catchUpFrom;
        LocalDateTime since = catchUp != null && catchUp.isBefore(openSince) ? catchUp : openSince;
        for (Payment payment : paymentRepository.findByProviderAndMethodAndStatusAndUpdatedAtAfter(
                PaymentProvider.BAKONG, PaymentMethod.QR, PaymentStatus.PENDING, since)) {
            String key = payment.getId() + ":" + payment.getExternalReference();
            int done = checksDone.getOrDefault(key, 0);
            if (done >= CHECK_AT.length && catchUp == null) {
                continue;
            }
            Duration age = qrAge(payment);
            if (done < CHECK_AT.length && age != null && age.compareTo(CHECK_AT[done]) < 0) {
                continue;
            }
            checksDone.put(key, done + 1);
            lastChecked.put(key, now);
            check(payment);
            if (now.isBefore(bakongPausedUntil)) {
                break;
            }
        }
        if (catchUp != null && !Instant.now().isBefore(bakongPausedUntil)) {
            catchUpFrom = null;
        }
        Instant cutoff = now.minus(OPEN_FOR);
        lastFailureNotice.keySet().removeIf(id -> lastFailureNotice.get(id).isBefore(cutoff));
        lastChecked.keySet().removeIf(key -> {
            boolean old = lastChecked.get(key).isBefore(cutoff);
            if (old) {
                checksDone.remove(key);
            }
            return old;
        });
    }

    /** How long the customer has had the current KHQR; null when the code's time is unknown. */
    private static Duration qrAge(Payment payment) {
        LocalDateTime shownAt = payment.getQrExpiresAt() != null
                ? payment.getQrExpiresAt().minus(QR_LIFETIME)
                : payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
        return shownAt == null ? null : Duration.between(shownAt, LocalDateTime.now());
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
                if (catchUpFrom == null) {
                    catchUpFrom = LocalDateTime.now().minus(OPEN_FOR);
                }
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
