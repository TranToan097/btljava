package com.bantraicay.model;

import java.math.BigDecimal;

/**
 * Thuc the anh xa bang orders luu hoa don va tien do xuat tung dia thuc te.
 */
public class DonHang {

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_DISPENSING = "DISPENSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_JAM_ERROR = "JAM_ERROR";

    private String orderId;
    private String createdAt;
    private BigDecimal unitPrice;
    private int quantityOrdered;
    private int quantityDispensed;
    private BigDecimal totalPaid;
    private String orderStatus;

    public DonHang() {
        this.unitPrice = BigDecimal.ZERO;
        this.totalPaid = BigDecimal.ZERO;
    }

    public DonHang(String orderId, String createdAt, BigDecimal unitPrice,
                   int quantityOrdered, int quantityDispensed, BigDecimal totalPaid, String orderStatus) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.quantityOrdered = quantityOrdered;
        this.quantityDispensed = quantityDispensed;
        this.totalPaid = totalPaid != null ? totalPaid : BigDecimal.ZERO;
        this.orderStatus = orderStatus;
    }

    /**
     * Kiem tra toan bo so luong dia trong don da roi thanh cong hay chua.
     */
    public boolean isFullyDispensed() {
        return quantityOrdered > 0 && quantityDispensed >= quantityOrdered;
    }

    /**
     * Tinh so tien can hoan tra neu don hang bi ket giua chung.
     */
    public BigDecimal getRefundAmount() {
        int remaining = Math.max(0, quantityOrdered - quantityDispensed);
        if (unitPrice == null || remaining == 0) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(remaining));
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
    }

    public int getQuantityOrdered() {
        return quantityOrdered;
    }

    public void setQuantityOrdered(int quantityOrdered) {
        this.quantityOrdered = quantityOrdered;
    }

    public int getQuantityDispensed() {
        return quantityDispensed;
    }

    public void setQuantityDispensed(int quantityDispensed) {
        this.quantityDispensed = quantityDispensed;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid != null ? totalPaid : BigDecimal.ZERO;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }
}
