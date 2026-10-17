package com.x.payment.gateway;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a dynamic KHQR with the National Bank of Cambodia SDK. The code pays straight into the
 * merchant's own Bakong account, so no gateway account or API token is needed to show it.
 */
@Component
public class BakongKhqrGenerator {
    static final Duration QR_LIFETIME = Duration.ofMinutes(10);
    /** Field limits from the NBC KHQR SDK specification. */
    private static final int MERCHANT_NAME_LIMIT = 25;
    private static final int BILL_NUMBER_LIMIT = 25;

    public BakongKhqr generate(
            String bakongAccountId, String merchantName, String billNumber, BigDecimal amount, String currencyCode) {
        if (!StringUtils.hasText(bakongAccountId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bakong account ID is required");
        }
        KHQRCurrency currency = khqrCurrency(currencyCode);
        LocalDateTime expiresAt = LocalDateTime.now().plus(QR_LIFETIME);

        IndividualInfo info = new IndividualInfo();
        info.setBakongAccountId(bakongAccountId.trim());
        info.setMerchantName(limit(merchantName, MERCHANT_NAME_LIMIT));
        info.setCurrency(currency);
        // Riel has no minor unit, so KHQR amounts in KHR are whole numbers.
        info.setAmount(currency == KHQRCurrency.KHR
                ? amount.setScale(0, RoundingMode.HALF_UP).doubleValue()
                : amount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        info.setBillNumber(limit(billNumber, BILL_NUMBER_LIMIT));
        info.setExpirationTimestamp(expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        KHQRResponse<KHQRData> response = BakongKHQR.generateIndividual(info);
        KHQRData data = response.getData();
        if (response.getKHQRStatus() == null || response.getKHQRStatus().getCode() != 0 || data == null) {
            String reason = response.getKHQRStatus() == null ? "unknown error" : response.getKHQRStatus().getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KHQR could not be created: " + reason);
        }
        return new BakongKhqr(data.getQr(), data.getMd5(), svgDataUrl(data.getQr()), expiresAt.toString());
    }

    private KHQRCurrency khqrCurrency(String currencyCode) {
        String code = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "USD" -> KHQRCurrency.USD;
            case "KHR" -> KHQRCurrency.KHR;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KHQR supports only USD and KHR");
        };
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }

    private String svgDataUrl(String payload) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2));
        } catch (WriterException exception) {
            throw new IllegalStateException("KHQR image could not be drawn", exception);
        }
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "
                + matrix.getWidth() + ' ' + matrix.getHeight() + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                + "<path fill=\"#000000\" d=\"" + path + "\"/></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    /** A generated KHQR: the payload string, its MD5 (Bakong's lookup key) and a scannable SVG image. */
    public record BakongKhqr(String payload, String md5, String imageDataUrl, String expiresAt) {
    }
}
