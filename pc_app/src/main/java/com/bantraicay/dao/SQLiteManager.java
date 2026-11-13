package com.bantraicay.dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Singleton quan ly ket noi SQLite (cau hinh WAL + busy_timeout) va khoi tao schema 4 bang.
 */
public class SQLiteManager {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static volatile SQLiteManager instance;
    private String dbFilePath = "vending_machine.db";

    private SQLiteManager() {
    }

    public static SQLiteManager getInstance() {
        if (instance == null) {
            synchronized (SQLiteManager.class) {
                if (instance == null) {
                    instance = new SQLiteManager();
                }
            }
        }
        return instance;
    }

    /**
     * Cho phep doi duong dan file DB (chu yeu phuc vu kiem thu tu dong).
     */
    public synchronized void setDbFilePath(String dbFilePath) {
        this.dbFilePath = dbFilePath;
    }

    public synchronized String getDbFilePath() {
        return dbFilePath;
    }

    /**
     * Mo ket noi toi SQLite va cau hinh PRAGMA chong loi SQLITE_BUSY khi doc/ghi dong thoi.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=5000;");
            stmt.execute("PRAGMA journal_mode=WAL;");
        }
        return conn;
    }

    /**
     * Tao 4 bang du lieu chuan hoa va chen du lieu mac dinh ban dau neu chua co.
     */
    public synchronized void initTables() {
        String createMachineStateTable = """
                CREATE TABLE IF NOT EXISTS machine_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    fruit_name TEXT NOT NULL,
                    unit_price NUMERIC NOT NULL,
                    current_stock INTEGER NOT NULL CHECK (current_stock >= 0),
                    max_capacity INTEGER NOT NULL,
                    latest_temp REAL DEFAULT 0.0,
                    latest_humidity REAL DEFAULT 0.0,
                    machine_status TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """;

        String createOrdersTable = """
                CREATE TABLE IF NOT EXISTS orders (
                    order_id TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    unit_price NUMERIC NOT NULL,
                    quantity_ordered INTEGER NOT NULL CHECK (quantity_ordered > 0),
                    quantity_dispensed INTEGER NOT NULL DEFAULT 0,
                    total_paid NUMERIC NOT NULL,
                    order_status TEXT NOT NULL
                );
                """;

        String createRealtimeLogsTable = """
                CREATE TABLE IF NOT EXISTS realtime_event_logs (
                    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    system_timestamp TEXT NOT NULL,
                    esp_timestamp TEXT,
                    event_type TEXT NOT NULL,
                    order_id TEXT,
                    raw_message TEXT,
                    note TEXT
                );
                """;

        String createAdminAccountsTable = """
                CREATE TABLE IF NOT EXISTS admin_accounts (
                    username TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    last_login_at TEXT
                );
                """;

        String seedMachineStateSql = """
                INSERT OR IGNORE INTO machine_state (
                    id, fruit_name, unit_price, current_stock, max_capacity,
                    latest_temp, latest_humidity, machine_status, updated_at
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String seedAdminSql = """
                INSERT OR IGNORE INTO admin_accounts (username, password_hash, last_login_at)
                VALUES (?, ?, NULL);
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createMachineStateTable);
            stmt.execute(createOrdersTable);
            stmt.execute(createRealtimeLogsTable);
            stmt.execute(createAdminAccountsTable);

            try (PreparedStatement psMachine = conn.prepareStatement(seedMachineStateSql)) {
                psMachine.setString(1, "Dĩa Trái Cây Tươi Tổng Hợp");
                psMachine.setDouble(2, 25000.0);
                psMachine.setInt(3, 10);
                psMachine.setInt(4, 15);
                psMachine.setDouble(5, 16.5);
                psMachine.setDouble(6, 72.0);
                psMachine.setString(7, "READY");
                psMachine.setString(8, nowString());
                psMachine.executeUpdate();
            }

            try (PreparedStatement psAdmin = conn.prepareStatement(seedAdminSql)) {
                psAdmin.setString(1, "admin");
                psAdmin.setString(2, hashSha256("admin123"));
                psAdmin.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SQLiteManager] Loi khoi tao co so du lieu: " + e.getMessage());
        }
    }

    public static String nowString() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    /**
     * Bam mat khau theo thuat toan SHA-256 de luu an toan trong bang admin_accounts.
     */
    public static String hashSha256(String rawText) {
        if (rawText == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Khong tim thay thuat toan SHA-256", e);
        }
    }
}
