package com.x.payment.gateway;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.KHQRDecodeData;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BakongKhqrGeneratorTest {
    private final BakongKhqrGenerator generator = new BakongKhqrGenerator();

    @Test
    void generatesValidUsdKhqrForTheMerchantAccount() {
        BakongKhqrGenerator.BakongKhqr khqr = generator.generate(
                "tinhlok_shop@aclb", "TinhLok Coffee", "ORD-1001", new BigDecimal("12.50"), "usd");

        assertThat(BakongKHQR.verify(khqr.payload()).getData().isValid()).isTrue();
        KHQRDecodeData decoded = BakongKHQR.decode(khqr.payload()).getData();
        assertThat(decoded.getBakongAccountID()).isEqualTo("tinhlok_shop@aclb");
        assertThat(decoded.getMerchantName()).isEqualTo("TinhLok Coffee");
        assertThat(decoded.getBillNumber()).isEqualTo("ORD-1001");
        assertThat(decoded.getTransactionCurrency()).isEqualTo("840");
        assertThat(new BigDecimal(decoded.getTransactionAmount())).isEqualByComparingTo("12.50");
        assertThat(khqr.md5()).matches("[0-9a-f]{32}");
        assertThat(LocalDateTime.parse(khqr.expiresAt())).isAfter(LocalDateTime.now());
    }

    @Test
    void rendersTheKhqrAsAnSvgImage() {
        BakongKhqrGenerator.BakongKhqr khqr = generator.generate(
                "tinhlok_shop@aclb", "TinhLok Coffee", "ORD-1002", new BigDecimal("3.00"), "USD");

        assertThat(khqr.imageDataUrl()).startsWith("data:image/svg+xml;base64,");
        String svg = new String(Base64.getDecoder().decode(
                khqr.imageDataUrl().substring("data:image/svg+xml;base64,".length())), StandardCharsets.UTF_8);
        assertThat(svg).startsWith("<svg").contains("<path").endsWith("</svg>");
    }

    @Test
    void svgImageScansBackToTheExactKhqr() throws Exception {
        BakongKhqrGenerator.BakongKhqr khqr = generator.generate(
                "tinhlok_shop@aclb", "TinhLok Coffee", "ORD-1006", new BigDecimal("7.25"), "USD");
        String svg = new String(Base64.getDecoder().decode(
                khqr.imageDataUrl().substring("data:image/svg+xml;base64,".length())), StandardCharsets.UTF_8);

        // Rasterise the SVG modules (each "M x y h1v1h-1z" is one dark square) and scan them like a phone would.
        Matcher size = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(size.find()).isTrue();
        int modules = Integer.parseInt(size.group(1));
        int scale = 4;
        int[] pixels = new int[modules * scale * modules * scale];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        Matcher square = Pattern.compile("M(\\d+) (\\d+)h1v1h-1z").matcher(svg);
        while (square.find()) {
            int x = Integer.parseInt(square.group(1));
            int y = Integer.parseInt(square.group(2));
            for (int dy = 0; dy < scale; dy++) {
                for (int dx = 0; dx < scale; dx++) {
                    pixels[(y * scale + dy) * modules * scale + x * scale + dx] = 0xFF000000;
                }
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(modules * scale, modules * scale, pixels)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(khqr.payload());
    }

    @Test
    void usesWholeRielAndShortensLongMerchantNames() {
        BakongKhqrGenerator.BakongKhqr khqr = generator.generate(
                "tinhlok_shop@aclb", "TinhLok Mini Mart Phnom Penh Branch", "ORD-1003",
                new BigDecimal("10500.40"), "KHR");

        KHQRDecodeData decoded = BakongKHQR.decode(khqr.payload()).getData();
        assertThat(decoded.getTransactionCurrency()).isEqualTo("116");
        assertThat(new BigDecimal(decoded.getTransactionAmount())).isEqualByComparingTo("10500");
        assertThat(decoded.getMerchantName()).hasSizeLessThanOrEqualTo(25).startsWith("TinhLok Mini Mart");
    }

    @Test
    void rejectsCurrenciesKhqrCannotCarry() {
        assertThatThrownBy(() -> generator.generate(
                "tinhlok_shop@aclb", "TinhLok Coffee", "ORD-1004", new BigDecimal("5.00"), "EUR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("USD and KHR");
    }

    @Test
    void requiresABakongAccount() {
        assertThatThrownBy(() -> generator.generate(
                " ", "TinhLok Coffee", "ORD-1005", new BigDecimal("5.00"), "USD"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bakong account ID");
    }
}
