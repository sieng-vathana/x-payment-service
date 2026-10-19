package com.x.payment.alert;

import java.math.BigDecimal;
import java.util.List;

/** Extra facts for a payment alert; any of them may be missing. */
public record PaymentAlertDetails(
        String storeName,
        String orderNo,
        List<Item> items,
        String customerName,
        String cashierName) {

    public static final PaymentAlertDetails EMPTY = new PaymentAlertDetails(null, null, List.of(), null, null);

    public PaymentAlertDetails {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(String name, int quantity, BigDecimal total) { }
}
