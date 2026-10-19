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
                payer, reference, LocalDateTime.of(2026, 10, 19, 12, 31));
    }

    @Test
    void bankConfirmedPaymentReadsLikeAReceipt() {
        PaymentAlertDetails details = new PaymentAlertDetails("Shopify", "POS-B797D8BF-C6D", List.of(
                new PaymentAlertDetails.Item("Ice Cream", 1, new BigDecimal("0.25"))), null, "Sieng Vathana");

        assertThat(TelegramPaymentAlerts.text(payment("0.25", "USD", "Ice Cream x1", false, "aclbkhppxxx@aclb",
                "efc2930f1a2b"), details, UTC)).isEqualTo("""
                ✅ <b>KHQR Payment Received</b>
                <b>$0.25</b> · Shopify

                🧾 <b>Order POS-B797D8BF-C6D</b>
                • 1 × Ice Cream  <b>$0.25</b>

                <b>Cashier:</b> Sieng Vathana
                <b>Paid with:</b> ACLEDA Bank
                <b>Time:</b> 19 Oct 2026, 19:31
                <b>Ref:</b> <code>efc2930f</code>""");
    }

    @Test
    void payerShowsTheBankAndTheCustomerAccountOnlyWhenItIsReal() {
        assertThat(TelegramPaymentAlerts.payer("aclbkhppxxx@aclb")).isEqualTo("ACLEDA Bank");
        assertThat(TelegramPaymentAlerts.payer("sok_dara@abaa")).isEqualTo("ABA Bank (sok_dara@abaa)");
        assertThat(TelegramPaymentAlerts.payer("someone@newb")).isEqualTo("NEWB (someone@newb)");
        assertThat(TelegramPaymentAlerts.payer(" ")).isNull();
    }

    @Test
    void cashierConfirmationIsFlaggedAndTheNoteStandsInForTheItems() {
        String text = TelegramPaymentAlerts.text(payment("41000.00", "KHR", "Rice x1", true, null, "manual-1"),
                PaymentAlertDetails.EMPTY, UTC);

        assertThat(text).startsWith("⚠️ <b>KHQR Payment · Marked Paid by Cashier</b>\n<i>Not confirmed by Bakong.")
                .contains("<b>៛41,000</b>\n\nRice x1\n")
                .doesNotContain("Paid with")
                .doesNotContain("Customer");
    }

    @Test
    void longOrdersAreShortenedAndNamesAreEscaped() {
        List<PaymentAlertDetails.Item> items = IntStream.rangeClosed(1, 9)
                .mapToObj(i -> new PaymentAlertDetails.Item("Item <" + i + ">", 1, null)).toList();
        String text = TelegramPaymentAlerts.text(payment("9", "USD", null, false, null, null),
                new PaymentAlertDetails("A & B Shop", null, items, "Tom & Jerry", null), UTC);

        assertThat(text).contains("· A &amp; B Shop").contains("• 1 × Item &lt;6&gt;").doesNotContain("Item &lt;7&gt;")
                .contains("<i>+3 more items</i>").contains("<b>Customer:</b> Tom &amp; Jerry");
    }
}
