package com.x.payment.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Tells the merchant's Telegram chat when a KHQR payment arrives, after the payment is saved. */
@Component
@RequiredArgsConstructor
public class TelegramPaymentAlerts {
    static final int MAX_ITEMS = 6;
    private static final ZoneId CAMBODIA = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final BusinessAlertClient businessAlertClient;
    private final PaymentAlertDetailsClient detailsClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQrPaymentReceived(QrPaymentReceived payment) {
        businessAlertClient.send(payment.businessId(), payment.storeId(), "KHQR_PAYMENT",
                text(payment, detailsClient.find(payment), ZoneId.systemDefault()), "HTML");
    }

    /**
     * The alert in Telegram HTML. paidAt is in the server's zone and shown in Cambodia time.
     * Lines whose facts are missing are left out.
     */
    static String text(QrPaymentReceived payment, PaymentAlertDetails details, ZoneId serverZone) {
        StringBuilder text = new StringBuilder();
        if (payment.confirmedByCashier()) {
            text.append("⚠️ <b>KHQR Payment · Marked Paid by Cashier</b>\n")
                    .append("<i>Not confirmed by Bakong. Check your bank app before handing over goods.</i>\n");
        } else {
            text.append("✅ <b>KHQR Payment Received</b>\n");
        }
        text.append("<b>").append(money(payment.amount(), payment.currencyCode())).append("</b>");
        if (StringUtils.hasText(details.storeName())) text.append(" · ").append(escape(details.storeName().trim()));
        text.append("\n\n");

        String purchase = purchase(details, payment);
        if (!purchase.isEmpty()) text.append(purchase).append('\n');

        field(text, "Customer", details.customerName());
        field(text, "Cashier", details.cashierName());
        field(text, "Paid with", payer(payment.payerAccount()));
        if (payment.paidAt() != null) {
            field(text, "Time", TIME.format(payment.paidAt().atZone(serverZone).withZoneSameInstant(CAMBODIA)));
        }
        if (StringUtils.hasText(payment.reference())) {
            String reference = payment.reference().trim();
            text.append("<b>Ref:</b> <code>").append(escape(reference.length() > 8 ? reference.substring(0, 8) : reference)).append("</code>\n");
        }
        return text.toString().stripTrailing();
    }

    /** The order number and what was bought; the POS note stands in when the order's items are unknown. */
    private static String purchase(PaymentAlertDetails details, QrPaymentReceived payment) {
        StringBuilder text = new StringBuilder();
        if (details.orderNo() != null) {
            text.append("🧾 <b>Order ").append(escape(details.orderNo())).append("</b>\n");
        }
        List<PaymentAlertDetails.Item> items = details.items();
        if (items.isEmpty()) {
            String note = PaymentAlertDetailsClient.clean(payment.note());
            if (note != null) text.append(escape(note)).append('\n');
            return text.toString();
        }
        items.stream().limit(MAX_ITEMS).forEach(item -> {
            text.append("• ").append(item.quantity()).append(" × ").append(escape(item.name()));
            if (item.total() != null) {
                text.append("  <b>").append(money(item.total(), payment.currencyCode())).append("</b>");
            }
            text.append('\n');
        });
        if (items.size() > MAX_ITEMS) {
            int more = items.size() - MAX_ITEMS;
            text.append("<i>+").append(more).append(more == 1 ? " more item" : " more items").append("</i>\n");
        }
        return text.toString();
    }

    /** Banks by the domain of their Bakong account IDs (the part after @). */
    private static final Map<String, String> BANKS = Map.of(
            "aclb", "ACLEDA Bank",
            "abaa", "ABA Bank",
            "wing", "Wing Bank",
            "cadi", "Canadia Bank",
            "ppcb", "Phnom Penh Commercial Bank",
            "bkrt", "Bakong");

    /**
     * The bank the customer paid from, and their account when Bakong names it. Some banks report only their own
     * settlement account, such as "aclbkhppxxx@aclb"; that is not the customer, so only the bank is shown.
     */
    static String payer(String account) {
        if (!StringUtils.hasText(account)) return null;
        String trimmed = account.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0) return trimmed;
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        String user = trimmed.substring(0, at).toLowerCase(Locale.ROOT);
        String bank = BANKS.getOrDefault(domain, domain.toUpperCase(Locale.ROOT));
        boolean bankOwnAccount = user.contains("xxx") || user.startsWith(domain + "kh");
        return bankOwnAccount ? bank : bank + " (" + trimmed + ")";
    }

    private static void field(StringBuilder text, String label, String value) {
        if (!StringUtils.hasText(value)) return;
        text.append("<b>").append(label).append(":</b> ").append(escape(value.trim())).append('\n');
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    static String money(BigDecimal amount, String currency) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        if ("KHR".equals(currency)) {
            return "៛" + new DecimalFormat("#,##0", symbols).format(amount.setScale(0, RoundingMode.HALF_UP));
        }
        String formatted = new DecimalFormat("#,##0.00", symbols).format(amount.setScale(2, RoundingMode.HALF_UP));
        return "USD".equals(currency) ? "$" + formatted : formatted + " " + currency;
    }

}
