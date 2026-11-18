/*
 * Firmware ESP32-S3 N16R8 - May Ban Trai Cay Tu Dong (1 Khay / 1 Cot)
 * Thu vien can cai trong Arduino IDE:
 *   1. ESP32Servo (Kevin Harrington)
 *   2. DHT sensor library (Adafruit) + Adafruit Unified Sensor
 *
 * Giao thuc USB Serial (Baudrate: 115200, ket thuc moi dong bang \n):
 *   Nhan tu Java:
 *     - DONGBOGIO_hh:mm:ss
 *     - XUATDIA_n
 *   Gui len Java:
 *     - MOITRUONG_nhiet_am_hh:mm:ss
 *     - XUATDIATRAICAY_OK_hh:mm:ss
 *     - XUATDIATRAICAY_KET_hh:mm:ss
 *     - PHATHIEN_GIANLAN_hh:mm:ss
 */

#include <Arduino.h>
#include <ESP32Servo.h>
#include <DHT.h>

// ==================== CAU HINH CHAN GPIO ESP32-S3 N16R8 ====================
#define SERVO_PIN         13   // Chan tin hieu PWM dieu khien Servo day dia
#define LM393_DO_PIN      14   // Chan DO cua module quang tro LM393 (nhan tia Laser)
#define LASER_PIN         12   // Chan cap/bat Diode Laser (hoac noi thang nguon 3.3V/5V)
#define DHT_PIN           4    // Chan DATA cam bien nhiet am DHT11 / DHT22
#define DHT_TYPE          DHT22

// Muc logic chan DO cua LM393 khi tia Laser BI CAT (dia trai cay roi ngang qua).
// Tuy module co the dao lai thanh LOW neu module LM393 xuat muc LOW khi toi.
#define LASER_BROKEN_LEVEL HIGH

// Goc quay Servo day dia (chinh lai cho khop co khi thuc te)
#define SERVO_REST_ANGLE  0
#define SERVO_PUSH_ANGLE  110
#define SERVO_HOLD_MS     600

#define DISPENSE_TIMEOUT_MS 5000UL   // Qua 5 giay khong cat tia Laser => Bao KET
#define ENV_INTERVAL_MS     5000UL   // Gui nhiet do / do am moi 5 giay
#define FRAUD_COOLDOWN_MS   2500UL   // Chong spam canh bao gian lan lien tuc

Servo trayServo;
DHT dht(DHT_PIN, DHT_TYPE);

// ==================== DONG HO MEM BANG MILLIS() ====================
unsigned long syncBaseSeconds = 0;
unsigned long syncMillis = 0;

unsigned long lastEnvMillis = 0;
unsigned long lastFraudAlertMillis = 0;
bool isDispensingActive = false;
String serialInputBuffer = "";

String getCurrentTimeString() {
  unsigned long elapsedSec = (millis() - syncMillis) / 1000UL;
  unsigned long currentSecOfDay = (syncBaseSeconds + elapsedSec) % 86400UL;

  unsigned long hh = currentSecOfDay / 3600UL;
  unsigned long mm = (currentSecOfDay % 3600UL) / 60UL;
  unsigned long ss = currentSecOfDay % 60UL;

  char buf[9];
  snprintf(buf, sizeof(buf), "%02lu:%02lu:%02lu", hh, mm, ss);
  return String(buf);
}

void syncClockFromString(const String& timeStr) {
  int firstColon = timeStr.indexOf(':');
  int secondColon = timeStr.lastIndexOf(':');
  if (firstColon > 0 && secondColon > firstColon) {
    unsigned long hh = timeStr.substring(0, firstColon).toInt();
    unsigned long mm = timeStr.substring(firstColon + 1, secondColon).toInt();
    unsigned long ss = timeStr.substring(secondColon + 1).toInt();
    if (hh < 24 && mm < 60 && ss < 60) {
      syncBaseSeconds = hh * 3600UL + mm * 60UL + ss;
      syncMillis = millis();
    }
  }
}

bool isLaserBeamBroken() {
  if (digitalRead(LM393_DO_PIN) == LASER_BROKEN_LEVEL) {
    delay(20); // Chong doi tin hieu (debounce) 20ms
    return digitalRead(LM393_DO_PIN) == LASER_BROKEN_LEVEL;
  }
  return false;
}

void pushOneDishWithServo() {
  trayServo.write(SERVO_PUSH_ANGLE);
  delay(SERVO_HOLD_MS);
  trayServo.write(SERVO_REST_ANGLE);
}

void executeDispenseQuantity(int quantity) {
  if (quantity <= 0) {
    return;
  }
  isDispensingActive = true;

  for (int i = 0; i < quantity; i++) {
    pushOneDishWithServo();

    unsigned long waitStart = millis();
    bool dishDetected = false;
    while (millis() - waitStart <= DISPENSE_TIMEOUT_MS) {
      if (isLaserBeamBroken()) {
        dishDetected = true;
        break;
      }
      delay(10);
    }

    if (dishDetected) {
      Serial.println("XUATDIATRAICAY_OK_" + getCurrentTimeString());
      // Cho dia roi han qua khoi tia Laser truoc khi day dia tiep theo
      unsigned long settleStart = millis();
      while (millis() - settleStart < 1200UL) {
        delay(20);
      }
    } else {
      Serial.println("XUATDIATRAICAY_KET_" + getCurrentTimeString());
      break; // Dung ngay vong lap xuat cac dia con lai neu bi ket
    }
  }

  lastFraudAlertMillis = millis();
  isDispensingActive = false;
}

void handleSerialCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) {
    return;
  }

  if (cmd.startsWith("DONGBOGIO_")) {
    String timePart = cmd.substring(String("DONGBOGIO_").length());
    syncClockFromString(timePart);
  } else if (cmd.startsWith("XUATDIA_")) {
    int qty = cmd.substring(String("XUATDIA_").length()).toInt();
    if (qty > 0) {
      executeDispenseQuantity(qty);
    }
  }
}

void sendEnvironmentTelemetry() {
  float hum = dht.readHumidity();
  float temp = dht.readTemperature();
  if (!isnan(hum) && !isnan(temp)) {
    String msg = "MOITRUONG_" + String(temp, 1) + "_" + String(hum, 1) + "_" + getCurrentTimeString();
    Serial.println(msg);
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(LM393_DO_PIN, INPUT);
  pinMode(LASER_PIN, OUTPUT);
  digitalWrite(LASER_PIN, HIGH); // Bat tia Laser lien tuc

  trayServo.attach(SERVO_PIN);
  trayServo.write(SERVO_REST_ANGLE);

  dht.begin();
  syncMillis = millis();
}

void loop() {
  // 1. Doc lenh tu Java qua USB Serial
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\n') {
      handleSerialCommand(serialInputBuffer);
      serialInputBuffer = "";
    } else if (c != '\r') {
      serialInputBuffer += c;
    }
  }

  // 2. Phat hien gian lan / roi bat thuong khi khong co lenh XUATDIA_n
  if (!isDispensingActive && (millis() - lastFraudAlertMillis > FRAUD_COOLDOWN_MS)) {
    if (isLaserBeamBroken()) {
      Serial.println("PHATHIEN_GIANLAN_" + getCurrentTimeString());
      lastFraudAlertMillis = millis();
    }
  }

  // 3. Dinh ky moi 5 giay gui nhiet do & do am len Java
  if (millis() - lastEnvMillis >= ENV_INTERVAL_MS) {
    lastEnvMillis = millis();
    sendEnvironmentTelemetry();
  }
}
