package com.bantraicay;

import com.bantraicay.controller.AdminDialogController;
import com.bantraicay.controller.KioskViewController;
import com.bantraicay.dao.SQLiteManager;
import com.bantraicay.dao.VendingRepository;
import com.bantraicay.hardware.BoGiaoTiepEsp32;
import com.bantraicay.hardware.Esp32MessageListener;
import com.bantraicay.model.BaoCaoDoiSoat;
import com.bantraicay.model.DonHang;
import com.bantraicay.model.RealtimeEventLog;
import com.bantraicay.model.TrangThaiMay;
import com.bantraicay.service.PayOSPaymentService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VendingSystemIntegrationTest {

    @TempDir
    Path tempDir;

    private String originalDbPath;
    private VendingRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteManager manager = SQLiteManager.getInstance();
        originalDbPath = manager.getDbFilePath();
        manager.setDbFilePath(tempDir.resolve("test_vending.db").toString());
        manager.initTables();
        repository = new VendingRepository();
    }

    @AfterEach
    void tearDown() {
        SQLiteManager.getInstance().setDbFilePath(originalDbPath);
    }

    @Test
    void testDatabaseInitializationCreatesFourTablesAndWalMode() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection conn = SQLiteManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            try (ResultSet pragmaRs = stmt.executeQuery("PRAGMA journal_mode;")) {
                assertTrue(pragmaRs.next());
                assertEquals("wal", pragmaRs.getString(1).toLowerCase());
            }
        }
        assertTrue(tables.contains("machine_state"));
        assertTrue(tables.contains("orders"));
        assertTrue(tables.contains("realtime_event_logs"));
        assertTrue(tables.contains("admin_accounts"));

        TrangThaiMay state = repository.getMachineState();
        assertEquals(10, state.getCurrentStock());
        assertEquals(TrangThaiMay.STATUS_READY, state.getMachineStatus());
    }

    @Test
    void testAdminLoginVerificationAndLogSeparation() {
        assertFalse(repository.verifyAdminLogin("admin", "wrongpass"));
        assertTrue(repository.verifyAdminLogin("admin", "admin123"));

        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(), "18:00:00", "MOITRUONG", null,
                "MOITRUONG_18.5_70.0_18:00:00", "Temp 18.5"));

        List<RealtimeEventLog> activityLogs = repository.getSystemActivityLogs(50);
        List<RealtimeEventLog> envLogs = repository.getEnvironmentLogs(50);

        assertEquals(2, activityLogs.size());
        assertTrue(activityLogs.stream().allMatch(l -> "ADMIN_LOGIN".equals(l.getEventType())));
        assertEquals(1, envLogs.size());
        assertEquals("MOITRUONG", envLogs.get(0).getEventType());
    }

    @Test
    void testQrPaymentDispenseJamFraudAndReconciliation() {
        PayOSPaymentService payService = new PayOSPaymentService();
        DonHang order = new DonHang("ORD-TEST-01", SQLiteManager.nowString(),
                BigDecimal.valueOf(25000), 2, 0, BigDecimal.valueOf(50000), "PENDING_QR");

        String qrContent = payService.createPaymentQrSession(order);
        assertTrue(qrContent.contains("ORD-TEST-01"));
        Image qrImage = payService.generateQrImage(qrContent, 160);
        assertNotNull(qrImage);
        assertEquals(160.0, qrImage.getWidth());

        assertTrue(payService.markPaymentReceivedManual(order));
        assertEquals(DonHang.STATUS_PAID, order.getOrderStatus());

        repository.insertOrder(order);
        order.setOrderStatus(DonHang.STATUS_DISPENSING);
        repository.updateOrderProgress(order.getOrderId(), 0, DonHang.STATUS_DISPENSING);

        // Dish 1 OK -> stock 10 -> 9
        TrangThaiMay after1 = repository.processDispenseOkForOrder(order, "18:10:01");
        assertEquals(9, after1.getCurrentStock());
        assertEquals(1, order.getQuantityDispensed());
        assertEquals(DonHang.STATUS_DISPENSING, order.getOrderStatus());

        // Dish 2 Jammed -> stock remains 9, machine JAMMED, refund 25000
        TrangThaiMay afterJam = repository.processDispenseJamForOrder(order, "18:10:07");
        assertEquals(TrangThaiMay.STATUS_JAMMED, afterJam.getMachineStatus());
        assertEquals(DonHang.STATUS_JAM_ERROR, order.getOrderStatus());
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(order.getRefundAmount()));

        // Abnormal drop alert
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(), "18:15:00", "PHATHIEN_GIANLAN", null,
                "PHATHIEN_GIANLAN_18:15:00", "Laser cat ngoai phien mua"));

        List<DonHang> history = repository.getOrderHistory(10);
        assertEquals(1, history.size());
        assertEquals(DonHang.STATUS_JAM_ERROR, history.get(0).getOrderStatus());

        BaoCaoDoiSoat summary = repository.getReconciliationSummary(null);
        assertEquals(2, summary.getTotalDishesPaid());
        assertEquals(1, summary.getTotalDishesDispensedOk());
        assertEquals(1, summary.getTotalJammedCount());
        assertEquals(1, summary.getAbnormalDropCount());
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(summary.getNetRevenue()));
        assertEquals(0, BigDecimal.valueOf(25000).compareTo(summary.getRefundAmount()));
        assertFalse(summary.isMatched());
    }

    @Test
    void testBoGiaoTiepEsp32MultipleListenersAndUartTrimming() {
        BoGiaoTiepEsp32 comm = BoGiaoTiepEsp32.getInstance();
        List<String> listener1Events = new ArrayList<>();
        List<String> listener2Events = new ArrayList<>();

        Esp32MessageListener l1 = new Esp32MessageListener() {
            @Override public void onEnvironmentUpdate(String t, double temp, double hum) {
                listener1Events.add("ENV:" + temp + ":" + hum + ":" + t);
            }
            @Override public void onDishDispensedSuccess(String t) { listener1Events.add("OK:" + t); }
            @Override public void onDishJammed(String t) { listener1Events.add("KET:" + t); }
            @Override public void onAbnormalDropAlert(String t) { listener1Events.add("GIANLAN:" + t); }
            @Override public void onRawSerialTraffic(String d, String raw) {}
        };
        Esp32MessageListener l2 = new Esp32MessageListener() {
            @Override public void onEnvironmentUpdate(String t, double temp, double hum) {
                listener2Events.add("ENV:" + temp + ":" + hum + ":" + t);
            }
            @Override public void onDishDispensedSuccess(String t) { listener2Events.add("OK:" + t); }
            @Override public void onDishJammed(String t) { listener2Events.add("KET:" + t); }
            @Override public void onAbnormalDropAlert(String t) { listener2Events.add("GIANLAN:" + t); }
            @Override public void onRawSerialTraffic(String d, String raw) {}
        };

        comm.addListener(l1);
        comm.addListener(l2);
        try {
            // Simulate \r from Serial.println() and noisy malformed lines
            comm.parseIncomingLine("MOITRUONG_18.5_72.0_17:35:05\r\n");
            comm.parseIncomingLine("XUATDIATRAICAY_OK_17:35:12\r");
            comm.parseIncomingLine("MOITRUONG_NOISE_BAD\r");
            comm.parseIncomingLine("XUATDIATRAICAY_KET_17:35:17\r");
            comm.parseIncomingLine("PHATHIEN_GIANLAN_17:40:05\r");

            assertEquals(4, listener1Events.size());
            assertEquals(listener1Events, listener2Events);
            assertEquals("ENV:18.5:72.0:17:35:05", listener1Events.get(0));
            assertEquals("OK:17:35:12", listener1Events.get(1));
            assertEquals("KET:17:35:17", listener1Events.get(2));
            assertEquals("GIANLAN:17:40:05", listener1Events.get(3));
        } finally {
            comm.removeListener(l1);
            comm.removeListener(l2);
        }
    }

    @Test
    void testConcurrentSqliteWritesDoNotThrowBusy() throws Exception {
        int threads = 4;
        int writesPerThread = 25;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < writesPerThread; i++) {
                        repository.updateEnvironment(16.0 + tid, 70.0 + i % 5);
                        repository.insertRealtimeLog(new RealtimeEventLog(
                                SQLiteManager.nowString(), "18:00:00", "MOITRUONG",
                                null, "SIM_" + tid + "_" + i, "Concurrent test"));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
        assertEquals(threads * writesPerThread, repository.getEnvironmentLogs(500).size());
    }

    @Test
    void testFxmlViewsAndResourcesLoadCleanly() throws Exception {
        assertNotNull(getClass().getResource("/com/bantraicay/css/style.css"));
        assertNotNull(getClass().getResource("/com/bantraicay/images/fruit_plate.png"));

        CountDownLatch fxReady = new CountDownLatch(1);
        try {
            Platform.startup(fxReady::countDown);
        } catch (IllegalStateException alreadyStarted) {
            fxReady.countDown();
        }
        assertTrue(fxReady.await(10, TimeUnit.SECONDS));

        CountDownLatch loadDone = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            KioskViewController kioskCtrl = null;
            AdminDialogController adminCtrl = null;
            try {
                FXMLLoader kioskLoader = new FXMLLoader(
                        getClass().getResource("/com/bantraicay/view/KioskView.fxml"));
                Parent kioskRoot = kioskLoader.load();
                kioskCtrl = kioskLoader.getController();
                assertNotNull(kioskRoot);
                assertNotNull(kioskCtrl);

                FXMLLoader adminLoader = new FXMLLoader(
                        getClass().getResource("/com/bantraicay/view/AdminDialog.fxml"));
                Parent adminRoot = adminLoader.load();
                adminCtrl = adminLoader.getController();
                assertNotNull(adminRoot);
                assertNotNull(adminCtrl);
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                if (adminCtrl != null) {
                    adminCtrl.cleanupOnClose();
                }
                if (kioskCtrl != null) {
                    kioskCtrl.cleanupOnExit();
                }
                loadDone.countDown();
            }
        });

        assertTrue(loadDone.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            fail("Loi tai FXML/Controller: " + errorRef.get().getMessage(), errorRef.get());
        }
    }
}
