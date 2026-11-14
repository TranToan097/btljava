package com.bantraicay.dao;

import com.bantraicay.model.BaoCaoDoiSoat;
import com.bantraicay.model.DonHang;
import com.bantraicay.model.RealtimeEventLog;
import com.bantraicay.model.TrangThaiMay;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lop truy xuat du lieu SQLite voi toan bo phuong thuc ghi duoc synchronized chong SQLITE_BUSY.
 */
public class VendingRepository {

    /**
     * Xac thuc tai khoan dang nhap trang Quan tri va ghi nhan su kien ADMIN_LOGIN thoi gian thuc.
     */
    public synchronized boolean verifyAdminLogin(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return false;
        }
        String trimmedUser = username.trim();
        String expectedHash = SQLiteManager.hashSha256(password);
        String selectSql = "SELECT password_hash FROM admin_accounts WHERE username = ?";
        String updateLoginSql = "UPDATE admin_accounts SET last_login_at = ? WHERE username = ?";

        boolean authenticated = false;
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, trimmedUser);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    authenticated = expectedHash.equals(storedHash);
                }
            }
            if (authenticated) {
                try (PreparedStatement psUpdate = conn.prepareStatement(updateLoginSql)) {
                    psUpdate.setString(1, SQLiteManager.nowString());
                    psUpdate.setString(2, trimmedUser);
                    psUpdate.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi verifyAdminLogin: " + e.getMessage());
            return false;
        }

        insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "ADMIN_LOGIN",
                null,
                null,
                authenticated
                        ? "Dang nhap Quan tri thanh cong: " + trimmedUser
                        : "Dang nhap Quan tri that bai voi tai khoan: " + trimmedUser
        ));
        return authenticated;
    }

    public TrangThaiMay getMachineState() {
        String sql = "SELECT fruit_name, unit_price, current_stock, max_capacity, "
                + "latest_temp, latest_humidity, machine_status, updated_at "
                + "FROM machine_state WHERE id = 1";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new TrangThaiMay(
                        rs.getString("fruit_name"),
                        BigDecimal.valueOf(rs.getDouble("unit_price")),
                        rs.getInt("current_stock"),
                        rs.getInt("max_capacity"),
                        rs.getDouble("latest_temp"),
                        rs.getDouble("latest_humidity"),
                        rs.getString("machine_status"),
                        rs.getString("updated_at")
                );
            }
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi getMachineState: " + e.getMessage());
        }
        return new TrangThaiMay();
    }

    public synchronized void updateStockAndStatus(int newStock, String status) {
        String sql = "UPDATE machine_state SET current_stock = ?, machine_status = ?, updated_at = ? WHERE id = 1";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, newStock));
            ps.setString(2, status);
            ps.setString(3, SQLiteManager.nowString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi updateStockAndStatus: " + e.getMessage());
        }
    }

    public synchronized void updateEnvironment(double temp, double hum) {
        String sql = "UPDATE machine_state SET latest_temp = ?, latest_humidity = ?, updated_at = ? WHERE id = 1";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, temp);
            ps.setDouble(2, hum);
            ps.setString(3, SQLiteManager.nowString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi updateEnvironment: " + e.getMessage());
        }
    }

    public synchronized void updateProductInfo(String name, BigDecimal price, int stock, int maxCapacity) {
        int safeMax = Math.max(1, maxCapacity);
        int safeStock = Math.max(0, Math.min(stock, safeMax));
        String nextStatus = safeStock > 0 ? TrangThaiMay.STATUS_READY : TrangThaiMay.STATUS_OUT_OF_STOCK;

        String sql = "UPDATE machine_state SET fruit_name = ?, unit_price = ?, current_stock = ?, "
                + "max_capacity = ?, machine_status = ?, updated_at = ? WHERE id = 1";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, price.doubleValue());
            ps.setInt(3, safeStock);
            ps.setInt(4, safeMax);
            ps.setString(5, nextStatus);
            ps.setString(6, SQLiteManager.nowString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi updateProductInfo: " + e.getMessage());
        }
    }

    public synchronized void insertOrder(DonHang donHang) {
        String sql = "INSERT INTO orders (order_id, created_at, unit_price, quantity_ordered, "
                + "quantity_dispensed, total_paid, order_status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, donHang.getOrderId());
            ps.setString(2, donHang.getCreatedAt());
            ps.setDouble(3, donHang.getUnitPrice().doubleValue());
            ps.setInt(4, donHang.getQuantityOrdered());
            ps.setInt(5, donHang.getQuantityDispensed());
            ps.setDouble(6, donHang.getTotalPaid().doubleValue());
            ps.setString(7, donHang.getOrderStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi insertOrder: " + e.getMessage());
        }
    }

    public synchronized void updateOrderProgress(String orderId, int dispensedQty, String status) {
        String sql = "UPDATE orders SET quantity_dispensed = ?, order_status = ? WHERE order_id = ?";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dispensedQty);
            ps.setString(2, status);
            ps.setString(3, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi updateOrderProgress: " + e.getMessage());
        }
    }

    public synchronized void insertRealtimeLog(RealtimeEventLog log) {
        String sql = "INSERT INTO realtime_event_logs (system_timestamp, esp_timestamp, event_type, "
                + "order_id, raw_message, note) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getSystemTimestamp() != null ? log.getSystemTimestamp() : SQLiteManager.nowString());
            ps.setString(2, log.getEspTimestamp());
            ps.setString(3, log.getEventType());
            ps.setString(4, log.getOrderId());
            ps.setString(5, log.getRawMessage());
            ps.setString(6, log.getNote());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi insertRealtimeLog: " + e.getMessage());
        }
    }

    /**
     * Lay danh sach lich su giao dich (bang orders) phuc vu man hinh Quan tri.
     */
    public List<DonHang> getOrderHistory(int limit) {
        List<DonHang> result = new ArrayList<>();
        String sql = "SELECT order_id, created_at, unit_price, quantity_ordered, "
                + "quantity_dispensed, total_paid, order_status "
                + "FROM orders ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new DonHang(
                            rs.getString("order_id"),
                            rs.getString("created_at"),
                            BigDecimal.valueOf(rs.getDouble("unit_price")),
                            rs.getInt("quantity_ordered"),
                            rs.getInt("quantity_dispensed"),
                            BigDecimal.valueOf(rs.getDouble("total_paid")),
                            rs.getString("order_status")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi getOrderHistory: " + e.getMessage());
        }
        return result;
    }

    /**
     * Lay log hoat dong he thong (loai tru log MOITRUONG de khong bi troi log giao dich/loi).
     */
    public List<RealtimeEventLog> getSystemActivityLogs(int limit) {
        return queryLogsByCondition("event_type != 'MOITRUONG'", limit);
    }

    /**
     * Lay log moi truong he thong (nhiet do & do am MOITRUONG).
     */
    public List<RealtimeEventLog> getEnvironmentLogs(int limit) {
        return queryLogsByCondition("event_type = 'MOITRUONG'", limit);
    }

    private List<RealtimeEventLog> queryLogsByCondition(String whereClause, int limit) {
        List<RealtimeEventLog> logs = new ArrayList<>();
        String sql = "SELECT log_id, system_timestamp, esp_timestamp, event_type, order_id, raw_message, note "
                + "FROM realtime_event_logs WHERE " + whereClause + " ORDER BY log_id DESC LIMIT ?";
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new RealtimeEventLog(
                            rs.getLong("log_id"),
                            rs.getString("system_timestamp"),
                            rs.getString("esp_timestamp"),
                            rs.getString("event_type"),
                            rs.getString("order_id"),
                            rs.getString("raw_message"),
                            rs.getString("note")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi truy van log: " + e.getMessage());
        }
        return logs;
    }

    /**
     * Tong hop bao cao doi soat 3 ben (Tien thu - Dia roi qua cam bien Laser - Loi ket/Gian lan).
     * Truyen datePrefix = null hoac rong de doi soat toan bo du lieu.
     */
    public BaoCaoDoiSoat getReconciliationSummary(String datePrefix) {
        String likePattern = (datePrefix != null && !datePrefix.isBlank()) ? datePrefix.trim() + "%" : "%";
        String orderSql = """
                SELECT
                    COALESCE(SUM(quantity_ordered), 0) AS total_paid_dishes,
                    COALESCE(SUM(quantity_dispensed * unit_price), 0) AS net_revenue,
                    COALESCE(SUM(CASE WHEN quantity_ordered > quantity_dispensed
                                      THEN (quantity_ordered - quantity_dispensed) * unit_price
                                      ELSE 0 END), 0) AS refund_amount
                FROM orders
                WHERE created_at LIKE ?
                """;

        String logSql = """
                SELECT
                    COALESCE(SUM(CASE WHEN event_type = 'XUATDIATRAICAY_OK' THEN 1 ELSE 0 END), 0) AS laser_ok_count,
                    COALESCE(SUM(CASE WHEN event_type = 'XUATDIATRAICAY_KET' THEN 1 ELSE 0 END), 0) AS jammed_count,
                    COALESCE(SUM(CASE WHEN event_type = 'PHATHIEN_GIANLAN' THEN 1 ELSE 0 END), 0) AS abnormal_count
                FROM realtime_event_logs
                WHERE system_timestamp LIKE ?
                """;

        int totalPaid = 0;
        int totalLaserOk = 0;
        int totalJammed = 0;
        int abnormalDrops = 0;
        BigDecimal netRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;

        try (Connection conn = SQLiteManager.getInstance().getConnection()) {
            try (PreparedStatement psOrder = conn.prepareStatement(orderSql)) {
                psOrder.setString(1, likePattern);
                try (ResultSet rs = psOrder.executeQuery()) {
                    if (rs.next()) {
                        totalPaid = rs.getInt("total_paid_dishes");
                        netRevenue = BigDecimal.valueOf(rs.getDouble("net_revenue"));
                        refundAmount = BigDecimal.valueOf(rs.getDouble("refund_amount"));
                    }
                }
            }
            try (PreparedStatement psLog = conn.prepareStatement(logSql)) {
                psLog.setString(1, likePattern);
                try (ResultSet rs = psLog.executeQuery()) {
                    if (rs.next()) {
                        totalLaserOk = rs.getInt("laser_ok_count");
                        totalJammed = rs.getInt("jammed_count");
                        abnormalDrops = rs.getInt("abnormal_count");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[VendingRepository] Loi getReconciliationSummary: " + e.getMessage());
        }

        return new BaoCaoDoiSoat(totalPaid, totalLaserOk, totalJammed, abnormalDrops, netRevenue, refundAmount);
    }

    /**
     * Cap nhat dong bo SQLite khi co 1 dia roi qua cam bien Laser (XUATDIATRAICAY_OK).
     */
    public synchronized TrangThaiMay processDispenseOkForOrder(DonHang donHang, String espTime) {
        TrangThaiMay currentState = getMachineState();
        currentState.decrementStock();
        updateStockAndStatus(currentState.getCurrentStock(), currentState.getMachineStatus());

        if (donHang != null) {
            donHang.setQuantityDispensed(donHang.getQuantityDispensed() + 1);
            String nextOrderStatus = donHang.isFullyDispensed()
                    ? DonHang.STATUS_COMPLETED
                    : DonHang.STATUS_DISPENSING;
            donHang.setOrderStatus(nextOrderStatus);
            updateOrderProgress(donHang.getOrderId(), donHang.getQuantityDispensed(), nextOrderStatus);

            insertRealtimeLog(new RealtimeEventLog(
                    SQLiteManager.nowString(),
                    espTime,
                    "XUATDIATRAICAY_OK",
                    donHang.getOrderId(),
                    "XUATDIATRAICAY_OK_" + espTime,
                    String.format("Xuat thanh cong dia %d/%d (Ton kho con: %d)",
                            donHang.getQuantityDispensed(), donHang.getQuantityOrdered(), currentState.getCurrentStock())
            ));
        } else {
            insertRealtimeLog(new RealtimeEventLog(
                    SQLiteManager.nowString(),
                    espTime,
                    "XUATDIATRAICAY_OK",
                    null,
                    "XUATDIATRAICAY_OK_" + espTime,
                    "Nhan tin hieu XUATDIATRAICAY_OK ngoai don hang (Ton kho con: " + currentState.getCurrentStock() + ")"
            ));
        }
        return currentState;
    }

    /**
     * Cap nhat dong bo SQLite khi gap loi ket dia (XUATDIATRAICAY_KET).
     */
    public synchronized TrangThaiMay processDispenseJamForOrder(DonHang donHang, String espTime) {
        TrangThaiMay currentState = getMachineState();
        currentState.setMachineStatus(TrangThaiMay.STATUS_JAMMED);
        updateStockAndStatus(currentState.getCurrentStock(), TrangThaiMay.STATUS_JAMMED);

        if (donHang != null) {
            donHang.setOrderStatus(DonHang.STATUS_JAM_ERROR);
            updateOrderProgress(donHang.getOrderId(), donHang.getQuantityDispensed(), DonHang.STATUS_JAM_ERROR);

            insertRealtimeLog(new RealtimeEventLog(
                    SQLiteManager.nowString(),
                    espTime,
                    "XUATDIATRAICAY_KET",
                    donHang.getOrderId(),
                    "XUATDIATRAICAY_KET_" + espTime,
                    String.format("Ket dia tai %d/%d. Can hoan tien: %,.0f VND",
                            donHang.getQuantityDispensed() + 1,
                            donHang.getQuantityOrdered(),
                            donHang.getRefundAmount().doubleValue())
            ));
        } else {
            insertRealtimeLog(new RealtimeEventLog(
                    SQLiteManager.nowString(),
                    espTime,
                    "XUATDIATRAICAY_KET",
                    null,
                    "XUATDIATRAICAY_KET_" + espTime,
                    "Phat hien ket khay (XUATDIATRAICAY_KET) - Da khoa may sang JAMMED"
            ));
        }
        return currentState;
    }
}
