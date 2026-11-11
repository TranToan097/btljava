package com.bantraicay.model;

import java.math.BigDecimal;

/**
 * Thuc the anh xa bang machine_state luu trang thai va ton kho cua khay trai cay duy nhat.
 */
public class TrangThaiMay {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_JAMMED = "JAMMED";
    public static final String STATUS_OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String STATUS_OVERHEAT = "OVERHEAT";

    private String fruitName;
    private BigDecimal unitPrice;
    private int currentStock;
    private int maxCapacity;
    private double latestTemp;
    private double latestHumidity;
    private String machineStatus;
    private String updatedAt;

    public TrangThaiMay() {
        this.unitPrice = BigDecimal.ZERO;
        this.machineStatus = STATUS_READY;
    }

    public TrangThaiMay(String fruitName, BigDecimal unitPrice, int currentStock, int maxCapacity,
                        double latestTemp, double latestHumidity, String machineStatus, String updatedAt) {
        this.fruitName = fruitName;
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.currentStock = currentStock;
        this.maxCapacity = maxCapacity;
        this.latestTemp = latestTemp;
        this.latestHumidity = latestHumidity;
        this.machineStatus = machineStatus;
        this.updatedAt = updatedAt;
    }

    /**
     * Kiem tra may co san sang ban so luong dia yeu cau hay khong.
     */
    public boolean canSell(int requestedQty) {
        return STATUS_READY.equalsIgnoreCase(machineStatus)
                && requestedQty > 0
                && requestedQty <= currentStock;
    }

    /**
     * Giam ton kho di 1 dia sau khi xac nhan roi qua cam bien Laser LM393.
     */
    public void decrementStock() {
        if (currentStock > 0) {
            currentStock--;
        }
        if (currentStock == 0 && STATUS_READY.equalsIgnoreCase(machineStatus)) {
            machineStatus = STATUS_OUT_OF_STOCK;
        }
    }

    public String getFruitName() {
        return fruitName;
    }

    public void setFruitName(String fruitName) {
        this.fruitName = fruitName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(int currentStock) {
        this.currentStock = Math.max(0, currentStock);
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public double getLatestTemp() {
        return latestTemp;
    }

    public void setLatestTemp(double latestTemp) {
        this.latestTemp = latestTemp;
    }

    public double getLatestHumidity() {
        return latestHumidity;
    }

    public void setLatestHumidity(double latestHumidity) {
        this.latestHumidity = latestHumidity;
    }

    public String getMachineStatus() {
        return machineStatus;
    }

    public void setMachineStatus(String machineStatus) {
        this.machineStatus = machineStatus;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
