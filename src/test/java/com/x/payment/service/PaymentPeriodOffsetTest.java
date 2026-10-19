package com.x.payment.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPeriodOffsetTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 19, 17, 0);

    @Test
    void phnomPenhIsSevenHoursAheadOfStoredUtcTimes() {
        assertEquals(420, PaymentService.offsetMinutes("Asia/Phnom_Penh", FROM));
        assertEquals(0, PaymentService.offsetMinutes(null, FROM));
    }

    @Test
    void unknownTimeZoneIsABadRequest() {
        assertThrows(ResponseStatusException.class, () -> PaymentService.offsetMinutes("Mars/Base", FROM));
    }
}
