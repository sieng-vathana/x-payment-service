package com.x.payment.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Tells the merchant's Telegram chat when a KHQR payment arrives, after the payment is saved. */
@Component
@RequiredArgsConstructor
public class TelegramPaymentAlerts {

    private final BusinessAlertClient businessAlertClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQrPaymentReceived(QrPaymentReceived payment) {
        businessAlertClient.send(payment.businessId(), payment.storeId(), "KHQR_PAYMENT", text(payment));
    }

    static String text(QrPaymentReceived payment) {
        StringBuilder text = new StringBuilder(payment.confirmedByCashier()
                ? "💰 KHQR payment confirmed by the cashier"
                : "💰 KHQR payment received")
                .append('\n').append(money(payment.amount(), payment.currencyCode()));
        if (StringUtils.hasText(payment.note())) {
            text.append('\n').append(payment.note().trim());
        }
        return text.toString();
    }

    private static String money(BigDecimal amount, String currency) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        if ("KHR".equals(currency)) {
            return "៛ " + new DecimalFormat("#,##0", symbols).format(amount.setScale(0, RoundingMode.HALF_UP));
        }
        String formatted = new DecimalFormat("#,##0.00", symbols).format(amount.setScale(2, RoundingMode.HALF_UP));
        return "USD".equals(currency) ? "$" + formatted : formatted + " " + currency;
    }
}
