package com.bantraicay.controller;

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
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller man hinh Quan tri & Doi soat (chi duoc mo sau khi xac thuc dang nhap).
 * Bao gom 5 phan khu: Ton kho, Mua thu nghiem QR, Lich su giao dich, Log hoat dong, Log moi truong & Doi soat.
 */
public class AdminDialogController implements Esp32MessageListener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TEST_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @FXML private ComboBox<String> cbComPorts;
    @FXML private Button btnConnectCom;
    @FXML private Label lblAdminComStatus;
    @FXML private CheckBox chkSimulationMode;
    @FXML private Label lblLiveTraffic;
    @FXML private TabPane adminTabPane;

    // Tab 1: Ton kho & Cau hinh
    @FXML private Label lblAdminMachineStatus;
    @FXML private Label lblAdminStockDisplay;
    @FXML private Label lblAdminEnvDisplay;
    @FXML private Label lblAdminUpdatedAt;
    @FXML private Button btnClearJam;
    @FXML private TextField txtFruitName;
    @FXML private TextField txtUnitPrice;
    @FXML private TextField txtMaxCapacity;
    @FXML private TextField txtAddStock;
    @FXML private Button btnSaveRestock;
    @FXML private Label lblRestockFeedback;

    // Tab 2: Mua thu nghiem (QR + Nut Da nhan duoc tien)
    @FXML private Label lblTestQty;
    @FXML private Label lblTestTotal;
    @FXML private Button btnTestShowQr;
    @FXML private ProgressBar progressTestDispense;
    @FXML private Label lblTestStatus;
    @FXML private VBox boxTestQrPanel;
    @FXML private ImageView imgTestQrCode;
    @FXML private Label lblTestOrderInfo;
    @FXML private Button btnTestPaymentReceived;
    @FXML private Button btnTestCancelQr;

    // Tab 3: Lich su giao dich
    @FXML private TableView<DonHang> tblOrderHistory;
    @FXML private TableColumn<DonHang, String> colOrderId;
    @FXML private TableColumn<DonHang, String> colOrderTime;
    @FXML private TableColumn<DonHang, String> colOrderUnitPrice;
    @FXML private TableColumn<DonHang, Number> colOrderQtyOrdered;
    @FXML private TableColumn<DonHang, Number> colOrderQtyDispensed;
    @FXML private TableColumn<DonHang, String> colOrderTotalPaid;
    @FXML private TableColumn<DonHang, String> colOrderRefund;
    @FXML private TableColumn<DonHang, String> colOrderStatus;

    // Tab 4: Log hoat dong he thong
    @FXML private TableView<RealtimeEventLog> tblActivityLogs;
    @FXML private TableColumn<RealtimeEventLog, Number> colActId;
    @FXML private TableColumn<RealtimeEventLog, String> colActSysTime;
    @FXML private TableColumn<RealtimeEventLog, String> colActEspTime;
    @FXML private TableColumn<RealtimeEventLog, String> colActEventType;
    @FXML private TableColumn<RealtimeEventLog, String> colActOrderId;
    @FXML private TableColumn<RealtimeEventLog, String> colActRawMsg;
    @FXML private TableColumn<RealtimeEventLog, String> colActNote;

    // Tab 5: Log moi truong & Doi soat
    @FXML private TextField txtReconcileDate;
    @FXML private Label lblReconcileMatchBadge;
    @FXML private Label lblRecPaidDishes;
    @FXML private Label lblRecLaserDishes;
    @FXML private Label lblRecJamCount;
    @FXML private Label lblRecFraudCount;
    @FXML private Label lblRecNetRevenue;
    @FXML private Label lblRecRefundAmount;
    @FXML private TableView<RealtimeEventLog> tblEnvLogs;
    @FXML private TableColumn<RealtimeEventLog, Number> colEnvId;
    @FXML private TableColumn<RealtimeEventLog, String> colEnvSysTime;
    @FXML private TableColumn<RealtimeEventLog, String> colEnvEspTime;
    @FXML private TableColumn<RealtimeEventLog, String> colEnvRawMsg;
    @FXML private TableColumn<RealtimeEventLog, String> colEnvNote;

    private final VendingRepository repository = new VendingRepository();
    private final BoGiaoTiepEsp32 boGiaoTiep = BoGiaoTiepEsp32.getInstance();
    private final PayOSPaymentService paymentService = new PayOSPaymentService();

    private Stage hostStage;
    private Runnable onDataChangedCallback;
    private TrangThaiMay currentState;

    private DonHang testOrder;
    private int testQty = 1;
    private volatile boolean testDispensing = false;

    @FXML
    public void initialize() {
        setupTableColumns();
        boGiaoTiep.addListener(this);
        chkSimulationMode.setSelected(boGiaoTiep.isSimulationMode());
        onRefreshPortsClicked();
        updateComStatusBanner();
        onRefreshAllTabsClicked();
    }

    public void setHostStageAndRefreshCallback(Stage hostStage, Runnable onDataChangedCallback) {
        this.hostStage = hostStage;
        this.onDataChangedCallback = onDataChangedCallback;
    }

    private void setupTableColumns() {
        // Tab 3: Orders
        colOrderId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOrderId()));
        colOrderTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt()));
        colOrderUnitPrice.setCellValueFactory(c -> new SimpleStringProperty(
                KioskViewController.formatCurrency(c.getValue().getUnitPrice())));
        colOrderQtyOrdered.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantityOrdered()));
        colOrderQtyDispensed.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantityDispensed()));
        colOrderTotalPaid.setCellValueFactory(c -> new SimpleStringProperty(
                KioskViewController.formatCurrency(c.getValue().getTotalPaid())));
        colOrderRefund.setCellValueFactory(c -> new SimpleStringProperty(
                KioskViewController.formatCurrency(c.getValue().getRefundAmount())));
        colOrderStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOrderStatus()));

        // Tab 4: System Activity Logs
        colActId.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().getLogId()));
        colActSysTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSystemTimestamp()));
        colActEspTime.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getEspTimestamp())));
        colActEventType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEventType()));
        colActOrderId.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getOrderId())));
        colActRawMsg.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getRawMessage())));
        colActNote.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getNote())));

        // Tab 5: Environment Logs
        colEnvId.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().getLogId()));
        colEnvSysTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSystemTimestamp()));
        colEnvEspTime.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getEspTimestamp())));
        colEnvRawMsg.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getRawMessage())));
        colEnvNote.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getNote())));
    }

    @FXML
    public void onRefreshAllTabsClicked() {
        currentState = repository.getMachineState();
        refreshTab1MachineAndForm();
        refreshTestPurchaseControls();
        tblOrderHistory.setItems(FXCollections.observableArrayList(repository.getOrderHistory(200)));
        tblActivityLogs.setItems(FXCollections.observableArrayList(repository.getSystemActivityLogs(300)));
        tblEnvLogs.setItems(FXCollections.observableArrayList(repository.getEnvironmentLogs(300)));
        onLoadReconciliationClicked();
        notifyParentKiosk();
    }

    private void refreshTab1MachineAndForm() {
        if (currentState == null) {
            return;
        }
        lblAdminMachineStatus.setText("Trạng thái máy: " + currentState.getMachineStatus());
        lblAdminStockDisplay.setText(String.format("Số lượng tồn: %d / %d dĩa",
                currentState.getCurrentStock(), currentState.getMaxCapacity()));
        lblAdminEnvDisplay.setText(String.format("Nhiệt độ / Độ ẩm: %.1f °C | %.1f %%",
                currentState.getLatestTemp(), currentState.getLatestHumidity()));
        lblAdminUpdatedAt.setText("Cập nhật gần nhất: " + nullSafe(currentState.getUpdatedAt()));

        btnClearJam.setDisable(!TrangThaiMay.STATUS_JAMMED.equalsIgnoreCase(currentState.getMachineStatus()));

        if (txtFruitName.getText().isBlank()) {
            txtFruitName.setText(currentState.getFruitName());
            txtUnitPrice.setText(String.valueOf(currentState.getUnitPrice().longValue()));
            txtMaxCapacity.setText(String.valueOf(currentState.getMaxCapacity()));
        }
    }

    // ==================== CONG COM & GIA LAP ====================

    @FXML
    private void onRefreshPortsClicked() {
        List<String> ports = boGiaoTiep.getAvailablePorts();
        cbComPorts.setItems(FXCollections.observableArrayList(ports));
        if (boGiaoTiep.isConnected()) {
            cbComPorts.setValue(boGiaoTiep.getConnectedPortName());
        } else if (!ports.isEmpty()) {
            cbComPorts.setValue(ports.get(0));
        }
    }

    @FXML
    private void onConnectComPortClicked() {
        if (boGiaoTiep.isConnected()) {
            boGiaoTiep.disconnect();
            updateComStatusBanner();
            notifyParentKiosk();
            return;
        }
        onConnectComPortClicked(cbComPorts.getValue());
    }

    public void onConnectComPortClicked(String portName) {
        if (portName == null || portName.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn cổng COM", "Vui lòng chọn cổng COM của mạch ESP32-S3.");
            return;
        }
        boolean ok = boGiaoTiep.connectAndSyncTime(portName);
        if (ok) {
            chkSimulationMode.setSelected(false);
            updateComStatusBanner();
        } else {
            showAlert(Alert.AlertType.ERROR, "Kết nối thất bại",
                    "Không thể mở cổng " + portName + ". Hãy kiểm tra dây cáp hoặc đóng Serial Monitor khác.");
        }
        notifyParentKiosk();
    }

    @FXML
    private void onToggleSimulationMode() {
        boGiaoTiep.setSimulationMode(chkSimulationMode.isSelected());
        updateComStatusBanner();
        notifyParentKiosk();
    }

    private void updateComStatusBanner() {
        if (boGiaoTiep.isConnected()) {
            btnConnectCom.setText("Ngắt COM");
            lblAdminComStatus.setText("Đã nối: " + boGiaoTiep.getConnectedPortName());
            lblAdminComStatus.getStyleClass().setAll("pill-ready");
        } else if (boGiaoTiep.isSimulationMode()) {
            btnConnectCom.setText("Kết nối");
            lblAdminComStatus.setText("Đang Giả lập");
            lblAdminComStatus.getStyleClass().setAll("pill-warning");
        } else {
            btnConnectCom.setText("Kết nối");
            lblAdminComStatus.setText("Chưa nối COM");
            lblAdminComStatus.getStyleClass().setAll("pill-danger");
        }
    }

    @FXML private void onSimOkClicked() {
        onSimulateEventClicked("XUATDIATRAICAY_OK_" + LocalTime.now().format(TIME_FMT));
    }

    @FXML private void onSimJamClicked() {
        onSimulateEventClicked("XUATDIATRAICAY_KET_" + LocalTime.now().format(TIME_FMT));
    }

    @FXML private void onSimFraudClicked() {
        onSimulateEventClicked("PHATHIEN_GIANLAN_" + LocalTime.now().format(TIME_FMT));
    }

    @FXML private void onSimEnvClicked() {
        onSimulateEventClicked("MOITRUONG_18.5_72.0_" + LocalTime.now().format(TIME_FMT));
    }

    @FXML private void onSimOverheatClicked() {
        onSimulateEventClicked("MOITRUONG_28.5_68.0_" + LocalTime.now().format(TIME_FMT));
    }

    public void onSimulateEventClicked(String rawMessage) {
        boGiaoTiep.simulateIncomingMessage(rawMessage);
    }

    // ==================== TAB 1: RESTOCK & UNLOCK ====================

    @FXML
    private void onRestockClicked() {
        try {
            String name = txtFruitName.getText().trim();
            if (name.isEmpty()) {
                lblRestockFeedback.setText("⚠️ Tên dĩa trái cây không được để trống.");
                return;
            }
            BigDecimal price = new BigDecimal(txtUnitPrice.getText().trim());
            int maxCap = Integer.parseInt(txtMaxCapacity.getText().trim());
            int addedQty = Integer.parseInt(txtAddStock.getText().trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0 || maxCap <= 0 || addedQty < 0) {
                lblRestockFeedback.setText("⚠️ Đơn giá và sức chứa phải > 0, số lượng nạp >= 0.");
                return;
            }
            currentState.setMaxCapacity(maxCap);
            onRestockClicked(name, price, addedQty);
            txtAddStock.setText("0");
        } catch (NumberFormatException e) {
            lblRestockFeedback.setText("⚠️ Vui lòng nhập đúng định dạng số cho Giá, Sức chứa và Số lượng nạp.");
        }
    }

    public void onRestockClicked(String name, BigDecimal price, int addedQty) {
        TrangThaiMay fresh = repository.getMachineState();
        int maxCap = currentState != null ? Math.max(1, currentState.getMaxCapacity()) : Math.max(1, fresh.getMaxCapacity());
        int baseStock = fresh.getCurrentStock();
        int newStock = Math.min(maxCap, baseStock + addedQty);

        repository.updateProductInfo(name, price, newStock, maxCap);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "ADMIN_RESTOCK",
                null,
                null,
                String.format("Admin cap nhat '%s', gia %s, nap +%d dia (Ton moi: %d/%d)",
                        name, KioskViewController.formatCurrency(price), addedQty, newStock, maxCap)
        ));
        lblRestockFeedback.setText("✅ Đã lưu cấu hình và cập nhật tồn kho thành công (" + newStock + "/" + maxCap + " dĩa).");
        onRefreshAllTabsClicked();
    }

    @FXML
    public void onClearJamStatusClicked() {
        TrangThaiMay latest = repository.getMachineState();
        String nextStatus = latest.getCurrentStock() > 0
                ? TrangThaiMay.STATUS_READY
                : TrangThaiMay.STATUS_OUT_OF_STOCK;
        repository.updateStockAndStatus(latest.getCurrentStock(), nextStatus);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "ADMIN_RESTOCK",
                null,
                null,
                "Admin da xoa loi ket khay (JAMMED -> " + nextStatus + ")"
        ));
        lblRestockFeedback.setText("✅ Đã mở khóa trạng thái máy về: " + nextStatus);
        onRefreshAllTabsClicked();
    }

    // ==================== TAB 2: MUA THU NGHIEM (QR + NUT DA NHAN TIEN) ====================

    @FXML
    private void onTestQtyDecClicked() {
        if (!testDispensing && testQty > 1) {
            testQty--;
            refreshTestPurchaseControls();
        }
    }

    @FXML
    private void onTestQtyIncClicked() {
        if (!testDispensing && currentState != null && testQty < currentState.getCurrentStock()) {
            testQty++;
            refreshTestPurchaseControls();
        }
    }

    private void refreshTestPurchaseControls() {
        if (currentState == null) {
            return;
        }
        if (testQty > Math.max(1, currentState.getCurrentStock())) {
            testQty = Math.max(1, currentState.getCurrentStock());
        }
        lblTestQty.setText(String.valueOf(testQty));
        BigDecimal total = currentState.getUnitPrice().multiply(BigDecimal.valueOf(testQty));
        lblTestTotal.setText("Tổng tiền thử nghiệm: " + KioskViewController.formatCurrency(total));
        btnTestShowQr.setDisable(testDispensing || !currentState.canSell(testQty) || !boGiaoTiep.isReadyToOperate());
    }

    @FXML
    private void onTestPurchaseQrClicked() {
        onTestPurchaseQrClicked(testQty);
    }

    public void onTestPurchaseQrClicked(int quantity) {
        if (testDispensing) {
            return;
        }
        currentState = repository.getMachineState();
        if (!currentState.canSell(quantity)) {
            showAlert(Alert.AlertType.WARNING, "Không thể tạo đơn thử",
                    "Máy đang ở trạng thái " + currentState.getMachineStatus() + " hoặc tồn kho không đủ!");
            return;
        }

        String testOrderId = "TEST-" + LocalDateTime.now().format(TEST_ID_FMT);
        BigDecimal total = currentState.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        testOrder = new DonHang(testOrderId, SQLiteManager.nowString(), currentState.getUnitPrice(),
                quantity, 0, total, "PENDING_QR");

        String qrPayload = paymentService.createPaymentQrSession(testOrder);
        Image qrImage = paymentService.generateQrImage(qrPayload, 190);
        imgTestQrCode.setImage(qrImage);
        lblTestOrderInfo.setText(String.format("Đơn thử: %s | SL: %d dĩa\nSố tiền: %s",
                testOrderId, quantity, KioskViewController.formatCurrency(total)));
        lblTestStatus.setText("Đang hiển thị mã QR thử nghiệm. Bấm 'Đã nhận được tiền' để kích hoạt xuất dĩa.");
        progressTestDispense.setProgress(0.0);

        btnTestPaymentReceived.setDisable(false);
        btnTestCancelQr.setDisable(false);
    }

    @FXML
    public void onTestPaymentReceivedClicked() {
        if (testOrder == null || testDispensing) {
            return;
        }
        if (!paymentService.markPaymentReceivedManual(testOrder)) {
            return;
        }
        testDispensing = true;
        btnTestPaymentReceived.setDisable(true);
        btnTestCancelQr.setDisable(true);
        btnTestShowQr.setDisable(true);

        testOrder.setCreatedAt(SQLiteManager.nowString());
        testOrder.setOrderStatus(DonHang.STATUS_PAID);
        repository.insertOrder(testOrder);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "ORDER_PAID",
                testOrder.getOrderId(),
                null,
                "Admin xac nhan da nhan tien don thu nghiem: " + KioskViewController.formatCurrency(testOrder.getTotalPaid())
        ));

        String cmd = "XUATDIA_" + testOrder.getQuantityOrdered();
        boolean sent = boGiaoTiep.requestDispenseDishes(testOrder.getQuantityOrdered());
        if (!sent) {
            testDispensing = false;
            testOrder.setOrderStatus(DonHang.STATUS_JAM_ERROR);
            repository.updateOrderProgress(testOrder.getOrderId(), 0, DonHang.STATUS_JAM_ERROR);
            lblTestStatus.setText("❌ Lỗi gửi lệnh xuống ESP32. Kiểm tra lại cổng COM hoặc bật Giả lập.");
            onRefreshAllTabsClicked();
            return;
        }

        testOrder.setOrderStatus(DonHang.STATUS_DISPENSING);
        repository.updateOrderProgress(testOrder.getOrderId(), 0, DonHang.STATUS_DISPENSING);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "CMD_XUATDIA",
                testOrder.getOrderId(),
                cmd,
                "Gui lenh xuat thu nghiem " + testOrder.getQuantityOrdered() + " dia"
        ));
        lblTestStatus.setText("⏳ Đã gửi " + cmd + ". Đang chờ phản hồi Laser từ ESP32...");
        onRefreshAllTabsClicked();
    }

    @FXML
    private void onTestCancelQrClicked() {
        if (testDispensing) {
            return;
        }
        testOrder = null;
        imgTestQrCode.setImage(null);
        lblTestOrderInfo.setText("Đã hủy đơn thử nghiệm.");
        lblTestStatus.setText("Chưa có giao dịch thử nghiệm.");
        btnTestPaymentReceived.setDisable(true);
        btnTestCancelQr.setDisable(true);
        refreshTestPurchaseControls();
    }

    // ==================== TAB 5: RECONCILIATION ====================

    @FXML
    public void onLoadReconciliationClicked() {
        String dateFilter = txtReconcileDate != null ? txtReconcileDate.getText() : null;
        BaoCaoDoiSoat summary = repository.getReconciliationSummary(dateFilter);

        lblRecPaidDishes.setText(String.valueOf(summary.getTotalDishesPaid()));
        lblRecLaserDishes.setText(String.valueOf(summary.getTotalDishesDispensedOk()));
        lblRecJamCount.setText(String.valueOf(summary.getTotalJammedCount()));
        lblRecFraudCount.setText(String.valueOf(summary.getAbnormalDropCount()));
        lblRecNetRevenue.setText(KioskViewController.formatCurrency(summary.getNetRevenue()));
        lblRecRefundAmount.setText(KioskViewController.formatCurrency(summary.getRefundAmount()));

        if (summary.isMatched()) {
            lblReconcileMatchBadge.setText("✅ KHỚP DỮ LIỆU 100%");
            lblReconcileMatchBadge.getStyleClass().setAll("pill-ready");
        } else {
            lblReconcileMatchBadge.setText("⚠️ PHÁT HIỆN LỆCH / SỰ CỐ");
            lblReconcileMatchBadge.getStyleClass().setAll("pill-danger");
        }
    }

    @FXML
    public void onLogoutClicked() {
        if (hostStage != null) {
            hostStage.close();
        }
    }

    public void cleanupOnClose() {
        boGiaoTiep.removeListener(this);
    }

    // ==================== Esp32MessageListener CALLBACKS ====================

    @Override
    public void onEnvironmentUpdate(String time, double temp, double hum) {
        Platform.runLater(this::onRefreshAllTabsClicked);
    }

    @Override
    public void onDishDispensedSuccess(String time) {
        if (testOrder != null && testDispensing) {
            repository.processDispenseOkForOrder(testOrder, time);
            boolean completed = testOrder.isFullyDispensed();
            if (completed) {
                testDispensing = false;
            }
            DonHang snapshot = testOrder;
            Platform.runLater(() -> {
                double p = (double) snapshot.getQuantityDispensed() / snapshot.getQuantityOrdered();
                progressTestDispense.setProgress(p);
                if (completed) {
                    lblTestStatus.setText(String.format("✅ Đơn thử %s hoàn tất (%d/%d dĩa lúc %s)!",
                            snapshot.getOrderId(), snapshot.getQuantityDispensed(), snapshot.getQuantityOrdered(), time));
                    testOrder = null;
                } else {
                    lblTestStatus.setText(String.format("⏳ Đơn thử %s đã rơi %d/%d dĩa (lúc %s)...",
                            snapshot.getOrderId(), snapshot.getQuantityDispensed(), snapshot.getQuantityOrdered(), time));
                }
                onRefreshAllTabsClicked();
            });
            return;
        }

        // Truong hop Admin bam nut "+ OK" gia lap khi khong co don thu nghiem nao dang chay
        repository.processDispenseOkForOrder(null, time);
        Platform.runLater(this::onRefreshAllTabsClicked);
    }

    @Override
    public void onDishJammed(String time) {
        if (testOrder != null && testDispensing) {
            repository.processDispenseJamForOrder(testOrder, time);
            testDispensing = false;
            DonHang snapshot = testOrder;
            Platform.runLater(() -> {
                lblTestStatus.setText(String.format(
                        "⛔ Đơn thử %s bị KẸT tại dĩa %d/%d lúc %s! Hoàn tiền: %s",
                        snapshot.getOrderId(), snapshot.getQuantityDispensed() + 1,
                        snapshot.getQuantityOrdered(), time,
                        KioskViewController.formatCurrency(snapshot.getRefundAmount())));
                testOrder = null;
                onRefreshAllTabsClicked();
            });
            return;
        }

        repository.processDispenseJamForOrder(null, time);
        Platform.runLater(this::onRefreshAllTabsClicked);
    }

    @Override
    public void onAbnormalDropAlert(String time) {
        Platform.runLater(this::onRefreshAllTabsClicked);
    }

    @Override
    public void onRawSerialTraffic(String direction, String rawLine) {
        Platform.runLater(() -> {
            lblLiveTraffic.setText("UART [" + direction + "]: " + rawLine);
            updateComStatusBanner();
        });
    }

    private void notifyParentKiosk() {
        if (onDataChangedCallback != null) {
            onDataChangedCallback.run();
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
