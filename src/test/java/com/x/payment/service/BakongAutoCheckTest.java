package com.x.payment.service;

import com.x.payment.entity.Payment;
import com.x.payment.entity.PaymentMethod;
import com.x.payment.entity.PaymentProvider;
import com.x.payment.entity.PaymentStatus;
import com.x.payment.realtime.PaymentRealtime;
import com.x.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BakongAutoCheckTest {

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentRealtime realtime = mock(PaymentRealtime.class);
    private final BakongAutoCheck autoCheck = new BakongAutoCheck(repository, paymentService, realtime);

    @Test
    void everyOpenKhqrIsCheckedWithBakong() {
        when(repository.findByProviderAndMethodAndStatusAndCreatedAtAfter(
                eq(PaymentProvider.BAKONG), eq(PaymentMethod.QR), eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(payment(41L), payment(42L)));

        autoCheck.checkOpenKhqrs();

        verify(paymentService).checkBakong(41L);
        verify(paymentService).checkBakong(42L);
    }

    @Test
    void aBakongOutageIsReportedToThePosOnceNotEveryRound() {
        when(paymentService.checkBakong(41L))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bakong could not be reached"));

        autoCheck.check(payment(41L));
        autoCheck.check(payment(41L));

        verify(realtime, times(1)).checkFailed(2L, 41L, "Bakong could not be reached");
    }

    @Test
    void otherErrorsAreNotShownToTheCashier() {
        when(paymentService.checkBakong(41L)).thenThrow(new IllegalStateException("row was updated by another transaction"));

        autoCheck.check(payment(41L));

        verify(realtime, never()).checkFailed(any(), any(), any());
    }

    private static Payment payment(Long id) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setBusinessId(2L);
        return payment;
    }
}
