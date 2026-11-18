package com.bantraicay;

import com.bantraicay.controller.KioskViewController;
import com.bantraicay.dao.SQLiteManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Lop khoi chay chinh cua ung dung JavaFX May Ban Trai Cay Tu Dong.
 */
public class UngDungMayBanTraiCay extends Application {

    private KioskViewController kioskController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        SQLiteManager.getInstance().initTables();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bantraicay/view/KioskView.fxml"));
        Parent root = loader.load();
        kioskController = loader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/bantraicay/css/style.css").toExternalForm());

        primaryStage.setTitle("Máy Bán Trái Cây Tự Động (Kiosk)");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (kioskController != null) {
            kioskController.cleanupOnExit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
