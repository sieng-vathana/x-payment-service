package com.x.payment.alert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramPaymentAlertsTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static QrPaymentReceived payment(String amount, String currency, String note, boolean byCashier,
                                             String payer, String reference) {
        return new QrPaymentReceived(7L, 2L, 3L, 40L, 9L, new BigDecimal(amount), currency, note, byCashier,
                payer, reference, LocalDateTime.of(2026, 10, 19, 10, 42));
    }

    @Test
    void bankConfirmedPaymentShowsTheWholeSale() {
        PaymentAlertDetails details = new PaymentAlertDetails("Main Store", "POS-0012", List.of(
                new PaymentAlertDetails.Item("Iced latte (Large)", 2, new BigDecimal("5.00")),
                new PaymentAlertDetails.Item("Croissant", 1, new BigDecimal("2.50"))), "Sok Dara", "Vathana");

        assertThat(TelegramPaymentAlerts.text(payment("7.5", "USD", "Iced latte x2", false, "sok_dara@aclb",
                "a1b2c3d4e5f6a7b8"), details, UTC)).isEqualTo("""
                ✅ <b>KHQR payment received</b>
                💵 <b>$7.50</b>
                🏪 Main Store

                🧾 Order <b>POS-0012</b>
                  • 2 × Iced latte (Large) — $5.00
                  • 1 × Croissant — $2.50

                👤 Customer: Sok Dara
                👔 Cashier: Vathana
                🏦 Paid from: sok_dara@aclb
                🕒 19 Oct 2026, 17:42
                🔖 Ref: <code>a1b2c3d4</code>""");
    }

    @Test
    void cashierConfirmationIsFlaggedAndTheNoteStandsInForTheItems() {
        String text = TelegramPaymentAlerts.text(payment("41000.00", "KHR", "Rice x1", true, null, "manual-1"),
                PaymentAlertDetails.EMPTY, UTC);

        assertThat(text).startsWith("⚠️ <b>KHQR payment marked as paid by the cashier</b>\n<i>Not confirmed by Bakong.")
                .contains("💵 <b>៛41,000</b>")
                .contains("\n🛒 Rice x1\n")
                .doesNotContain("Paid from")
                .doesNotContain("Customer");
    }

    @Test
    void longOrdersAreShortenedAndNamesAreEscaped() {
        List<PaymentAlertDetails.Item> items = IntStream.rangeClosed(1, 9)
                .mapToObj(i -> new PaymentAlertDetails.Item("Item <" + i + ">", 1, null)).toList();
        String text = TelegramPaymentAlerts.text(payment("9", "USD", null, false, null, null),
                new PaymentAlertDetails("A & B Shop", null, items, "Tom & Jerry", null), UTC);

        assertThat(text).contains("🏪 A &amp; B Shop").contains("• 1 × Item &lt;6&gt;").doesNotContain("Item &lt;7&gt;")
                .contains("<i>+3 more items</i>").contains("👤 Customer: Tom &amp; Jerry");
    }
}
