package com.x.payment.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import com.x.payment.entity.Payment;
import com.x.payment.repository.PaymentRepository;

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
    private final PaymentCustomerNameClient customerNameClient;
    private final PaymentRepository paymentRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQrPaymentReceived(QrPaymentReceived payment) {
        Long orderId = paymentRepository.findById(payment.paymentId()).map(Payment::getOrderId).orElse(null);
        String customerName = customerNameClient.findName(payment.businessId(), orderId);
        businessAlertClient.send(payment.businessId(), payment.storeId(), "KHQR_PAYMENT", text(payment, customerName));
    }

    static String text(QrPaymentReceived payment) {
        return text(payment, null);
    }

    static String text(QrPaymentReceived payment, String customerName) {
        StringBuilder text = new StringBuilder(payment.confirmedByCashier()
                ? "💰 KHQR payment confirmed by the cashier"
                : "💰 KHQR payment received")
                .append('\n').append(money(payment.amount(), payment.currencyCode()));
        if (StringUtils.hasText(customerName)) {
            text.append("\n👤 Customer: ").append(customerName.trim().replaceAll("[\\p{Cntrl}]", " "));
        }
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
