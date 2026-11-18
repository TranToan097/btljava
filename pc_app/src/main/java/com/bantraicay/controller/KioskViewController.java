package com.bantraicay.controller;

import com.bantraicay.dao.SQLiteManager;
import com.bantraicay.dao.VendingRepository;
import com.bantraicay.hardware.BoGiaoTiepEsp32;
import com.bantraicay.hardware.Esp32MessageListener;
import com.bantraicay.model.DonHang;
import com.bantraicay.model.RealtimeEventLog;
import com.bantraicay.model.TrangThaiMay;
import com.bantraicay.service.PayOSPaymentService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Controller man hinh Kiosk mac dinh danh cho khach mua hang (khi chua dang nhap Quan tri).
 */
public class KioskViewController implements Esp32MessageListener {

    private static final double OVERHEAT_THRESHOLD_C = 25.0;
    private static final DateTimeFormatter ORDER_ID_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @FXML private Label lblEnvironment;
    @FXML private Label lblOverheatAlert;
    @FXML private Label lblConnectionStatus;
    @FXML private Label lblMachineStatus;
    @FXML private Button btnOpenAdmin;

    @FXML private ImageView imgFruitPlate;
    @FXML private Label lblFruitName;
    @FXML private Label lblUnitPrice;
    @FXML private Label lblStockInfo;
    @FXML private Button btnDecreaseQty;
    @FXML private Label lblSelectedQty;
    @FXML private Button btnIncreaseQty;
    @FXML private Label lblTotalAmount;
    @FXML private Button btnShowPaymentQr;

    @FXML private VBox boxIdleGuide;
    @FXML private Label lblKioskNotice;
    @FXML private VBox boxQrPayment;
    @FXML private ImageView imgQrCode;
    @FXML private Label lblQrOrderInfo;
    @FXML private Button btnPaymentReceived;
    @FXML private Button btnCancelQr;

    @FXML private VBox boxDispenseStatus;
    @FXML private Label lblDispenseHeadline;
    @FXML private ProgressBar progressDispense;
    @FXML private Label lblDispenseDetail;
    @FXML private Button btnDismissResult;
    @FXML private Label lblLastActivity;

    private final VendingRepository repository = new VendingRepository();
    private final BoGiaoTiepEsp32 boGiaoTiep = BoGiaoTiepEsp32.getInstance();
    private final PayOSPaymentService paymentService = new PayOSPaymentService();

    private TrangThaiMay trangThaiMay;
    private DonHang currentOrder;
    private int selectedQuantity = 1;
    private volatile boolean isDispensing = false;
    private volatile boolean adminDialogOpen = false;

    @FXML
    public void initialize() {
        loadProductImage();
        boGiaoTiep.addListener(this);
        reloadStateAndRefreshUi();
    }

    private void loadProductImage() {
        try (InputStream is = getClass().getResourceAsStream("/com/bantraicay/images/fruit_plate.png")) {
            if (is != null) {
                imgFruitPlate.setImage(new Image(is));
            }
        } catch (IOException e) {
            System.err.println("[KioskViewController] Khong the tai anh fruit_plate.png: " + e.getMessage());
        }
    }

    public void reloadStateAndRefreshUi() {
        trangThaiMay = repository.getMachineState();
        if (selectedQuantity > Math.max(1, trangThaiMay.getCurrentStock())) {
            selectedQuantity = Math.max(1, trangThaiMay.getCurrentStock());
        }
        refreshUiControls();
    }

    private void refreshUiControls() {
        if (trangThaiMay == null) {
            return;
        }
        lblFruitName.setText(trangThaiMay.getFruitName());
        lblUnitPrice.setText(formatCurrency(trangThaiMay.getUnitPrice()) + " / dĩa");
        lblStockInfo.setText(String.format("Tồn kho khay: %d / %d dĩa",
                trangThaiMay.getCurrentStock(), trangThaiMay.getMaxCapacity()));

        lblSelectedQty.setText(String.valueOf(selectedQuantity));
        BigDecimal total = trangThaiMay.getUnitPrice().multiply(BigDecimal.valueOf(selectedQuantity));
        lblTotalAmount.setText("Tổng thanh toán: " + formatCurrency(total));

        updateEnvironmentHeader(trangThaiMay.getLatestTemp(), trangThaiMay.getLatestHumidity());
        updateConnectionHeader();
        updateMachineStatusHeader();
        updateInteractionLocks();
    }

    private void updateEnvironmentHeader(double temp, double hum) {
        lblEnvironment.setText(String.format("🌡️ %.1f °C  |  💧 %.1f %%", temp, hum));
        boolean overheat = temp > OVERHEAT_THRESHOLD_C;
        lblOverheatAlert.setVisible(overheat);
        lblOverheatAlert.setManaged(overheat);
    }

    private void updateConnectionHeader() {
        if (boGiaoTiep.isConnected()) {
            lblConnectionStatus.setText("🔌 ESP32: " + boGiaoTiep.getConnectedPortName());
            lblConnectionStatus.getStyleClass().setAll("pill-ready");
        } else if (boGiaoTiep.isSimulationMode()) {
            lblConnectionStatus.setText("🔌 ESP32: Giả lập");
            lblConnectionStatus.getStyleClass().setAll("pill-warning");
        } else {
            lblConnectionStatus.setText("⚠️ Chưa nối COM");
            lblConnectionStatus.getStyleClass().setAll("pill-danger");
        }
    }

    private void updateMachineStatusHeader() {
        String st = trangThaiMay.getMachineStatus();
        if (TrangThaiMay.STATUS_READY.equalsIgnoreCase(st)) {
            lblMachineStatus.setText("✅ SẴN SÀNG");
            lblMachineStatus.getStyleClass().setAll("pill-ready");
            lblKioskNotice.setVisible(false);
            lblKioskNotice.setManaged(false);
        } else if (TrangThaiMay.STATUS_JAMMED.equalsIgnoreCase(st)) {
            lblMachineStatus.setText("⛔ ĐANG KẸT KHAY");
            lblMachineStatus.getStyleClass().setAll("pill-danger");
            lblKioskNotice.setText("⚠️ Khay trái cây đang bị kẹt! Vui lòng liên hệ nhân viên Quản trị kiểm tra.");
            lblKioskNotice.setVisible(true);
            lblKioskNotice.setManaged(true);
        } else if (TrangThaiMay.STATUS_OUT_OF_STOCK.equalsIgnoreCase(st) || trangThaiMay.getCurrentStock() == 0) {
            lblMachineStatus.setText("📦 HẾT HÀNG");
            lblMachineStatus.getStyleClass().setAll("pill-warning");
            lblKioskNotice.setText("📦 Khay trái cây đã hết hàng. Vui lòng đợi Quản trị nạp thêm!");
            lblKioskNotice.setVisible(true);
            lblKioskNotice.setManaged(true);
        } else {
            lblMachineStatus.setText("⚠️ " + st);
            lblMachineStatus.getStyleClass().setAll("pill-danger");
        }
    }

    /**
     * Khoa toan bo nut mua hang khi dang DISPENSING, may bi ket, het hang hoac chua san sang ESP32.
     */
    private void updateInteractionLocks() {
        boolean waitingQrPayment = currentOrder != null && boxQrPayment.isVisible();
        boolean canBuyNow = !isDispensing
                && !waitingQrPayment
                && trangThaiMay.canSell(selectedQuantity)
                && boGiaoTiep.isReadyToOperate();

        btnShowPaymentQr.setDisable(!canBuyNow);
        btnDecreaseQty.setDisable(isDispensing || waitingQrPayment || selectedQuantity <= 1);
        btnIncreaseQty.setDisable(isDispensing || waitingQrPayment
                || selectedQuantity >= trangThaiMay.getCurrentStock());
        btnOpenAdmin.setDisable(isDispensing);
    }

    @FXML
    private void onDecreaseQtyClicked() {
        if (!isDispensing && selectedQuantity > 1) {
            selectedQuantity--;
            refreshUiControls();
        }
    }

    @FXML
    private void onIncreaseQtyClicked() {
        if (!isDispensing && trangThaiMay != null && selectedQuantity < trangThaiMay.getCurrentStock()) {
            selectedQuantity++;
            refreshUiControls();
        }
    }

    @FXML
    private void onShowPaymentQrClicked() {
        onShowPaymentQrClicked(selectedQuantity);
    }

    /**
     * Khoi tao don hang tam thoi, goi PayOSPaymentService tao ma QR va hien nut "Da nhan duoc tien".
     */
    public void onShowPaymentQrClicked(int quantity) {
        if (isDispensing) {
            return;
        }
        trangThaiMay = repository.getMachineState();
        if (!trangThaiMay.canSell(quantity)) {
            showErrorAlert("Không thể tạo đơn", "Máy đang tạm khóa hoặc số lượng tồn kho không đủ!");
            reloadStateAndRefreshUi();
            return;
        }
        if (!boGiaoTiep.isReadyToOperate()) {
            showErrorAlert("Chưa kết nối ESP32",
                    "Chưa kết nối cổng COM ESP32 và chế độ Giả lập đang tắt. Hãy mở Quản trị để cấu hình.");
            return;
        }

        String orderId = "ORD-" + LocalDateTime.now().format(ORDER_ID_TIME_FMT);
        BigDecimal unitPrice = trangThaiMay.getUnitPrice();
        BigDecimal totalPaid = unitPrice.multiply(BigDecimal.valueOf(quantity));

        currentOrder = new DonHang(orderId, SQLiteManager.nowString(), unitPrice,
                quantity, 0, totalPaid, "PENDING_QR");

        String qrPayload = paymentService.createPaymentQrSession(currentOrder);
        Image qrImage = paymentService.generateQrImage(qrPayload, 200);
        imgQrCode.setImage(qrImage);

        lblQrOrderInfo.setText(String.format("Mã đơn: %s  |  Số lượng: %d dĩa\nTổng tiền: %s",
                orderId, quantity, formatCurrency(totalPaid)));

        showRightPanelState(false, true, false);
        lblLastActivity.setText("Đã tạo mã QR cho đơn " + orderId + " - Chờ xác nhận nhận tiền.");
        updateInteractionLocks();
    }

    /**
     * Xu ly nut "Da nhan duoc tien": Luu DB PAID -> Khoa nut mua -> Gui XUATDIA_n xuong ESP32.
     */
    @FXML
    public void onPaymentReceivedButtonClicked() {
        if (currentOrder == null || isDispensing) {
            return;
        }
        if (!paymentService.markPaymentReceivedManual(currentOrder)) {
            showErrorAlert("Lỗi thanh toán", "Không thể xác nhận thanh toán cho đơn hàng này.");
            return;
        }

        // Khoa thao tac mua moi ngay lap tuc trong luc dang xuat dia
        isDispensing = true;
        currentOrder.setCreatedAt(SQLiteManager.nowString());
        currentOrder.setOrderStatus(DonHang.STATUS_PAID);
        repository.insertOrder(currentOrder);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "ORDER_PAID",
                currentOrder.getOrderId(),
                null,
                String.format("Khach xac nhan da thanh toan %s (%d dia)",
                        formatCurrency(currentOrder.getTotalPaid()), currentOrder.getQuantityOrdered())
        ));

        String cmd = "XUATDIA_" + currentOrder.getQuantityOrdered();
        boolean sent = boGiaoTiep.requestDispenseDishes(currentOrder.getQuantityOrdered());
        if (!sent) {
            isDispensing = false;
            currentOrder.setOrderStatus(DonHang.STATUS_JAM_ERROR);
            repository.updateOrderProgress(currentOrder.getOrderId(), 0, DonHang.STATUS_JAM_ERROR);
            showErrorAlert("Lỗi gửi lệnh ESP32", "Không gửi được lệnh " + cmd + " xuống ESP32. Kiểm tra lại cổng COM!");
            showRightPanelState(true, false, false);
            currentOrder = null;
            reloadStateAndRefreshUi();
            return;
        }

        currentOrder.setOrderStatus(DonHang.STATUS_DISPENSING);
        repository.updateOrderProgress(currentOrder.getOrderId(), 0, DonHang.STATUS_DISPENSING);
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                null,
                "CMD_XUATDIA",
                currentOrder.getOrderId(),
                cmd,
                "Da gui lenh xuat " + currentOrder.getQuantityOrdered() + " dia xuong ESP32"
        ));

        lblDispenseHeadline.setText("ĐANG XUẤT TRÁI CÂY...");
        progressDispense.setProgress(0.0);
        lblDispenseDetail.setText(String.format("Đang đẩy dĩa 1 / %d. Vui lòng chờ cảm biến Laser xác nhận...",
                currentOrder.getQuantityOrdered()));
        btnDismissResult.setVisible(false);
        btnDismissResult.setManaged(false);
        showRightPanelState(false, false, true);
        lblLastActivity.setText("Đang xuất " + currentOrder.getQuantityOrdered() + " dĩa cho đơn " + currentOrder.getOrderId());
        updateInteractionLocks();
    }

    @FXML
    private void onCancelQrClicked() {
        if (isDispensing) {
            return;
        }
        currentOrder = null;
        showRightPanelState(true, false, false);
        lblLastActivity.setText("Đã hủy mã QR chờ thanh toán.");
        updateInteractionLocks();
    }

    @FXML
    private void onDismissResultClicked() {
        if (isDispensing) {
            return;
        }
        currentOrder = null;
        showRightPanelState(true, false, false);
        reloadStateAndRefreshUi();
    }

    /**
     * Yeu cau dang nhap tai khoan Quan tri truoc khi mo giao dien AdminDialog.
     */
    @FXML
    public void onOpenAdminLoginClicked() {
        if (isDispensing) {
            showErrorAlert("Máy đang bận", "Đang xuất dĩa trái cây cho khách, vui lòng chờ hoàn tất trước khi vào Quản trị.");
            return;
        }

        Optional<Pair<String, String>> credentials = promptAdminCredentials();
        if (credentials.isEmpty()) {
            return;
        }

        String username = credentials.get().getKey();
        String password = credentials.get().getValue();
        if (!repository.verifyAdminLogin(username, password)) {
            showErrorAlert("Đăng nhập thất bại", "Sai tên đăng nhập hoặc mật khẩu Quản trị viên!");
            lblLastActivity.setText("Đăng nhập Quản trị thất bại (" + username + ").");
            return;
        }

        openAdminDialog();
    }

    private Optional<Pair<String, String>> promptAdminCredentials() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Đăng nhập Quản trị viên");
        dialog.setHeaderText("Xác thực quyền truy cập Trang Quản Trị\n(Mặc định: admin / admin123)");

        ButtonType loginButtonType = new ButtonType("Đăng nhập", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 10, 20));

        TextField txtUser = new TextField();
        txtUser.setPromptText("Tên đăng nhập");
        txtUser.setText("admin");

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Mật khẩu");

        grid.add(new Label("Tài khoản:"), 0, 0);
        grid.add(txtUser, 1, 0);
        grid.add(new Label("Mật khẩu:"), 0, 1);
        grid.add(txtPass, 1, 1);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(txtPass::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(txtUser.getText(), txtPass.getText());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void openAdminDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bantraicay/view/AdminDialog.fxml"));
            Parent root = loader.load();
            AdminDialogController adminController = loader.getController();

            Stage adminStage = new Stage();
            adminStage.setTitle("Quản trị & Đối soát - Máy Bán Trái Cây");
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/bantraicay/css/style.css").toExternalForm());
            adminStage.setScene(scene);
            adminStage.initModality(Modality.APPLICATION_MODAL);
            if (btnOpenAdmin.getScene() != null) {
                adminStage.initOwner(btnOpenAdmin.getScene().getWindow());
            }

            adminDialogOpen = true;
            adminController.setHostStageAndRefreshCallback(adminStage, () -> Platform.runLater(this::reloadStateAndRefreshUi));
            adminStage.setOnHidden(e -> {
                adminController.cleanupOnClose();
                adminDialogOpen = false;
                reloadStateAndRefreshUi();
                lblLastActivity.setText("Đã đăng xuất khỏi Quản trị, trở về màn hình Kiosk người mua.");
            });

            adminStage.showAndWait();
        } catch (IOException e) {
            adminDialogOpen = false;
            showErrorAlert("Lỗi mở cửa sổ Quản trị", "Không thể tải giao diện AdminDialog.fxml: " + e.getMessage());
        }
    }

    private void showRightPanelState(boolean idle, boolean qr, boolean dispense) {
        boxIdleGuide.setVisible(idle);
        boxIdleGuide.setManaged(idle);
        boxQrPayment.setVisible(qr);
        boxQrPayment.setManaged(qr);
        boxDispenseStatus.setVisible(dispense);
        boxDispenseStatus.setManaged(dispense);
    }

    // ==================== Esp32MessageListener CALLBACKS ====================

    @Override
    public void onEnvironmentUpdate(String time, double temp, double hum) {
        repository.updateEnvironment(temp, hum);
        String raw = String.format("MOITRUONG_%.1f_%.1f_%s", temp, hum, time);
        String note = String.format("Nhiệt độ: %.1f°C | Độ ẩm: %.1f%%%s",
                temp, hum, temp > OVERHEAT_THRESHOLD_C ? " [CẢNH BÁO QUÁ NHIỆT]" : "");
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(), time, "MOITRUONG", null, raw, note));

        Platform.runLater(() -> {
            if (trangThaiMay != null) {
                trangThaiMay.setLatestTemp(temp);
                trangThaiMay.setLatestHumidity(hum);
            }
            updateEnvironmentHeader(temp, hum);
            lblLastActivity.setText(String.format("Cập nhật môi trường (%s): %.1f °C | %.1f %%", time, temp, hum));
        });
    }

    @Override
    public void onDishDispensedSuccess(String time) {
        if (currentOrder != null && isDispensing) {
            trangThaiMay = repository.processDispenseOkForOrder(currentOrder, time);
            boolean completed = currentOrder.isFullyDispensed();
            if (completed) {
                isDispensing = false;
            }
            DonHang snapshot = currentOrder;
            Platform.runLater(() -> {
                double progress = (double) snapshot.getQuantityDispensed() / snapshot.getQuantityOrdered();
                progressDispense.setProgress(progress);
                if (completed) {
                    lblDispenseHeadline.setText("🎉 XUẤT HÀNG THÀNH CÔNG!");
                    lblDispenseDetail.setText(String.format(
                            "Đã xuất đủ %d / %d dĩa trái cây qua cảm biến Laser (lúc %s).\nChúc quý khách ngon miệng!",
                            snapshot.getQuantityDispensed(), snapshot.getQuantityOrdered(), time));
                    btnDismissResult.setVisible(true);
                    btnDismissResult.setManaged(true);
                    lblLastActivity.setText("Hoàn tất đơn " + snapshot.getOrderId() + " lúc " + time);
                } else {
                    lblDispenseDetail.setText(String.format(
                            "Đã xuất %d / %d dĩa thành công (lúc %s). Đang đẩy dĩa tiếp theo...",
                            snapshot.getQuantityDispensed(), snapshot.getQuantityOrdered(), time));
                    lblLastActivity.setText("Đơn " + snapshot.getOrderId() + ": Đã rơi "
                            + snapshot.getQuantityDispensed() + "/" + snapshot.getQuantityOrdered() + " dĩa");
                }
                refreshUiControls();
            });
            return;
        }

        if (!adminDialogOpen) {
            trangThaiMay = repository.processDispenseOkForOrder(null, time);
        }
        Platform.runLater(() -> {
            reloadStateAndRefreshUi();
            lblLastActivity.setText("Nhận tín hiệu XUATDIATRAICAY_OK lúc " + time);
        });
    }

    @Override
    public void onDishJammed(String time) {
        if (currentOrder != null && isDispensing) {
            trangThaiMay = repository.processDispenseJamForOrder(currentOrder, time);
            isDispensing = false;
            DonHang snapshot = currentOrder;
            Platform.runLater(() -> {
                lblDispenseHeadline.setText("⚠️ PHÁT HIỆN KẸT DĨA TRÁI CÂY!");
                lblDispenseDetail.setText(String.format(
                        "Đã nhận %d / %d dĩa. Dĩa tiếp theo bị kẹt lúc %s (quá 5s không cắt tia Laser).\n"
                                + "Máy đã tạm khóa an toàn. Số tiền cần hoàn lại: %s",
                        snapshot.getQuantityDispensed(), snapshot.getQuantityOrdered(),
                        time, formatCurrency(snapshot.getRefundAmount())));
                btnDismissResult.setVisible(true);
                btnDismissResult.setManaged(true);
                lblLastActivity.setText("LỖI KẸT ĐƠN " + snapshot.getOrderId() + " lúc " + time);
                refreshUiControls();
            });
            return;
        }

        if (!adminDialogOpen) {
            trangThaiMay = repository.processDispenseJamForOrder(null, time);
        }
        Platform.runLater(() -> {
            reloadStateAndRefreshUi();
            lblLastActivity.setText("⚠️ Nhận tín hiệu XUATDIATRAICAY_KET lúc " + time);
        });
    }

    @Override
    public void onAbnormalDropAlert(String time) {
        repository.insertRealtimeLog(new RealtimeEventLog(
                SQLiteManager.nowString(),
                time,
                "PHATHIEN_GIANLAN",
                null,
                "PHATHIEN_GIANLAN_" + time,
                "Cảnh báo cảm biến Laser LM393 bị cắt khi không có lệnh XUATDIA_n!"
        ));
        Platform.runLater(() -> lblLastActivity.setText(
                "🚨 CẢNH BÁO GIAN LẬN / RƠI BẤT THƯỜNG lúc " + time + " (Đã ghi DB đối soát)"));
    }

    @Override
    public void onRawSerialTraffic(String direction, String rawLine) {
        if ("TX".equals(direction) && rawLine != null && rawLine.startsWith("DONGBOGIO_")) {
            repository.insertRealtimeLog(new RealtimeEventLog(
                    SQLiteManager.nowString(),
                    rawLine.substring("DONGBOGIO_".length()),
                    "SYNC_TIME",
                    null,
                    rawLine,
                    "Đã đồng bộ thời gian thực từ Laptop xuống ESP32"
            ));
        }
        Platform.runLater(() -> {
            updateConnectionHeader();
            updateInteractionLocks();
        });
    }

    public void cleanupOnExit() {
        boGiaoTiep.removeListener(this);
        boGiaoTiep.disconnect();
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0 VNĐ";
        }
        DecimalFormat df = new DecimalFormat("#,##0");
        return df.format(amount) + " VNĐ";
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
