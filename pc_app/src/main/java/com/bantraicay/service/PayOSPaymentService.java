package com.bantraicay.service;

import com.bantraicay.model.DonHang;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Dich vu thanh toan tach rieng chuan bi san cho PayOS API.
 * Hien tai ho tro hien thi ma QR kem nut xac nhan "Da nhan duoc tien" o ca Kiosk va Admin.
 */
public class PayOSPaymentService {

    private String clientId = "PAYOS_CLIENT_ID_PLACEHOLDER";
    private String apiKey = "PAYOS_API_KEY_PLACEHOLDER";
    private String checksumKey = "PAYOS_CHECKSUM_KEY_PLACEHOLDER";
    private boolean sandboxManualMode = true;

    /**
     * Tao chuoi du lieu ma QR thanh toan cho don hang.
     * Khi tich hop PayOS API that, ham nay se goi endpoint tao Payment Link va tra ve chuoi qrCode tu PayOS.
     */
    public String createPaymentQrSession(DonHang donHang) {
        if (donHang == null) {
            return "";
        }
        long amount = donHang.getTotalPaid() != null ? donHang.getTotalPaid().longValue() : 0L;
        if (sandboxManualMode) {
            return String.format("PAYOS|ORDER:%s|AMOUNT:%d|QTY:%d|DESC:THANHTOAN_%s",
                    donHang.getOrderId(), amount, donHang.getQuantityOrdered(), donHang.getOrderId());
        }
        // Diem tich hop HTTP POST toi PayOS /v2/payment-requests trong tuong lai
        return String.format("PAYOS_LIVE|ORDER:%s|AMOUNT:%d", donHang.getOrderId(), amount);
    }

    /**
     * Sinh anh Image JavaFX tu chuoi QR bang thu vien ZXing.
     */
    public Image generateQrImage(String qrContent, int size) {
        if (qrContent == null || qrContent.isBlank()) {
            return null;
        }
        int safeSize = Math.max(120, size);
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, safeSize, safeSize, hints);

            WritableImage image = new WritableImage(safeSize, safeSize);
            PixelWriter pixelWriter = image.getPixelWriter();
            for (int y = 0; y < safeSize; y++) {
                for (int x = 0; x < safeSize; x++) {
                    pixelWriter.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return image;
        } catch (WriterException e) {
            System.err.println("[PayOSPaymentService] Loi tao anh QR: " + e.getMessage());
            return null;
        }
    }

    /**
     * Xac nhan thanh toan thu cong qua nut "Da nhan duoc tien" o che do hien tai.
     */
    public boolean markPaymentReceivedManual(DonHang donHang) {
        if (donHang == null || donHang.getQuantityOrdered() <= 0) {
            return false;
        }
        donHang.setOrderStatus(DonHang.STATUS_PAID);
        return true;
    }

    /**
     * Ham cho san de polling trang thai giao dich tu PayOS Webhook / API khi nang cap ban chinh thuc.
     */
    public boolean checkPayOSWebhookOrStatus(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        return !sandboxManualMode && clientId != null && apiKey != null && checksumKey != null;
    }

    public boolean isSandboxManualMode() {
        return sandboxManualMode;
    }

    public void setSandboxManualMode(boolean sandboxManualMode) {
        this.sandboxManualMode = sandboxManualMode;
    }

    public void configurePayOSCredentials(String clientId, String apiKey, String checksumKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
    }
}
