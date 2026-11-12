package com.bantraicay.model;

import java.math.BigDecimal;

/**
 * DTO tong hop ket qua doi soat 3 ben: Tien da thu - Dia roi qua Laser - Loi ket/Gian lan.
 */
public class BaoCaoDoiSoat {

    private int totalDishesPaid;
    private int totalDishesDispensedOk;
    private int totalJammedCount;
    private int abnormalDropCount;
    private BigDecimal netRevenue;
    private BigDecimal refundAmount;

    public BaoCaoDoiSoat() {
        this.netRevenue = BigDecimal.ZERO;
        this.refundAmount = BigDecimal.ZERO;
    }

    public BaoCaoDoiSoat(int totalDishesPaid, int totalDishesDispensedOk, int totalJammedCount,
                         int abnormalDropCount, BigDecimal netRevenue, BigDecimal refundAmount) {
        this.totalDishesPaid = totalDishesPaid;
        this.totalDishesDispensedOk = totalDishesDispensedOk;
        this.totalJammedCount = totalJammedCount;
        this.abnormalDropCount = abnormalDropCount;
        this.netRevenue = netRevenue != null ? netRevenue : BigDecimal.ZERO;
        this.refundAmount = refundAmount != null ? refundAmount : BigDecimal.ZERO;
    }

    /**
     * Kiem tra du lieu doi soat co khop hoan toan giua so dia tra tien, so dia Laser xac nhan va khong co gian lan/ket.
     */
    public boolean isMatched() {
        return totalDishesPaid == totalDishesDispensedOk
                && totalJammedCount == 0
                && abnormalDropCount == 0;
    }

    public int getTotalDishesPaid() {
        return totalDishesPaid;
    }

    public void setTotalDishesPaid(int totalDishesPaid) {
        this.totalDishesPaid = totalDishesPaid;
    }

    public int getTotalDishesDispensedOk() {
        return totalDishesDispensedOk;
    }

    public void setTotalDishesDispensedOk(int totalDishesDispensedOk) {
        this.totalDishesDispensedOk = totalDishesDispensedOk;
    }

    public int getTotalJammedCount() {
        return totalJammedCount;
    }

    public void setTotalJammedCount(int totalJammedCount) {
        this.totalJammedCount = totalJammedCount;
    }

    public int getAbnormalDropCount() {
        return abnormalDropCount;
    }

    public void setAbnormalDropCount(int abnormalDropCount) {
        this.abnormalDropCount = abnormalDropCount;
    }

    public BigDecimal getNetRevenue() {
        return netRevenue;
    }

    public void setNetRevenue(BigDecimal netRevenue) {
        this.netRevenue = netRevenue != null ? netRevenue : BigDecimal.ZERO;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount != null ? refundAmount : BigDecimal.ZERO;
    }
}
