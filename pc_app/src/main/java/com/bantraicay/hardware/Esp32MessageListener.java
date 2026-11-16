package com.bantraicay.hardware;

/**
 * Interface lang nghe cac su kien thoi gian thuc tu ESP32-S3 gui len qua USB Serial.
 */
public interface Esp32MessageListener {

    void onEnvironmentUpdate(String time, double temp, double hum);

    void onDishDispensedSuccess(String time);

    void onDishJammed(String time);

    void onAbnormalDropAlert(String time);

    void onRawSerialTraffic(String direction, String rawLine);
}
