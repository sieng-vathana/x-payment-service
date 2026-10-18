package com.x.payment.alert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramPaymentAlertsTest {

    @Test
    void alertShowsTheAmountAndWhatWasBought() {
        assertThat(TelegramPaymentAlerts.text(new QrPaymentReceived(
                7L, 2L, new BigDecimal("1234.5"), "USD", " Iced latte x2, Croissant x1 ", false)))
                .isEqualTo("💰 KHQR payment received\n$1,234.50\nIced latte x2, Croissant x1");
    }

    @Test
    void rielPaymentConfirmedByTheCashierSaysSo() {
        assertThat(TelegramPaymentAlerts.text(new QrPaymentReceived(
                8L, 2L, new BigDecimal("41000.00"), "KHR", null, true)))
                .isEqualTo("💰 KHQR payment confirmed by the cashier\n៛ 41,000");
    }
}
