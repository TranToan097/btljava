package com.bantraicay.hardware;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton quan ly cong COM USB Serial giao tiep voi ESP32-S3 (ho tro nhieu listener va che do gia lap).
 */
public class BoGiaoTiepEsp32 {

    private static final int BAUD_RATE = 115200;
    private static final long ESP32_RESET_DELAY_MS = 1500L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static volatile BoGiaoTiepEsp32 instance;

    private final List<Esp32MessageListener> listeners = new CopyOnWriteArrayList<>();
    private SerialPort comPort;
    private volatile boolean readingActive;
    private volatile boolean simulationMode = true;
    private Thread readerThread;

    private BoGiaoTiepEsp32() {
    }

    public static BoGiaoTiepEsp32 getInstance() {
        if (instance == null) {
            synchronized (BoGiaoTiepEsp32.class) {
                if (instance == null) {
                    instance = new BoGiaoTiepEsp32();
                }
            }
        }
        return instance;
    }

    public void addListener(Esp32MessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Esp32MessageListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public List<String> getAvailablePorts() {
        List<String> portNames = new ArrayList<>();
        for (SerialPort port : SerialPort.getCommPorts()) {
            portNames.add(port.getSystemPortName());
        }
        return portNames;
    }

    public synchronized boolean isConnected() {
        return comPort != null && comPort.isOpen();
    }

    public synchronized String getConnectedPortName() {
        return isConnected() ? comPort.getSystemPortName() : null;
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public boolean isReadyToOperate() {
        return isConnected() || simulationMode;
    }

    /**
     * Mo cong COM, go DTR/RTS chong reset, cho ~1.5s trong luong nen roi gui DONGBOGIO_hh:mm:ss.
     */
    public synchronized boolean connectAndSyncTime(String portName) {
        if (portName == null || portName.isBlank()) {
            return false;
        }
        disconnect();

        try {
            SerialPort targetPort = SerialPort.getCommPort(portName.trim());
            targetPort.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            targetPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 250, 0);

            if (!targetPort.openPort()) {
                return false;
            }

            targetPort.clearDTR();
            targetPort.clearRTS();

            this.comPort = targetPort;
            this.simulationMode = false;
            this.readingActive = true;
            startReaderThread();
            scheduleTimeSyncAfterReset(targetPort);
            return true;
        } catch (SerialPortInvalidPortException | NullPointerException e) {
            System.err.println("[BoGiaoTiepEsp32] Loi mo cong COM " + portName + ": " + e.getMessage());
            this.comPort = null;
            return false;
        }
    }

    private void scheduleTimeSyncAfterReset(SerialPort targetPort) {
        Thread syncThread = new Thread(() -> {
            try {
                Thread.sleep(ESP32_RESET_DELAY_MS);
                synchronized (BoGiaoTiepEsp32.this) {
                    if (comPort == targetPort && comPort.isOpen()) {
                        String syncCmd = "DONGBOGIO_" + LocalTime.now().format(TIME_FORMATTER);
                        sendRawLine(syncCmd);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "esp32-sync-time");
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private void startReaderThread() {
        readerThread = new Thread(() -> {
            StringBuilder lineBuffer = new StringBuilder();
            while (readingActive && comPort != null && comPort.isOpen()) {
                try {
                    int available = comPort.bytesAvailable();
                    if (available < 0) {
                        if (readingActive) {
                            notifyRawTraffic("ERR", "Cong COM da bi ngat ket noi dot ngot.");
                            disconnect();
                        }
                        break;
                    }
                    if (available > 0) {
                        byte[] buffer = new byte[available];
                        int readCount = comPort.readBytes(buffer, buffer.length);
                        if (readCount > 0) {
                            lineBuffer.append(new String(buffer, 0, readCount, StandardCharsets.UTF_8));
                            int newlineIndex;
                            while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
                                String fullLine = lineBuffer.substring(0, newlineIndex);
                                lineBuffer.delete(0, newlineIndex + 1);
                                parseIncomingLine(fullLine);
                            }
                        }
                    } else {
                        Thread.sleep(20L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (readingActive) {
                        notifyRawTraffic("ERR", "Loi doc du lieu COM: " + e.getMessage());
                        disconnect();
                    }
                    break;
                }
            }
        }, "esp32-serial-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void disconnect() {
        readingActive = false;
        if (comPort != null) {
            try {
                if (comPort.isOpen()) {
                    comPort.closePort();
                }
            } catch (Exception e) {
                System.err.println("[BoGiaoTiepEsp32] Loi dong cong COM: " + e.getMessage());
            } finally {
                comPort = null;
            }
        }
    }

    /**
     * Gui lenh XUATDIA_n xuong ESP32 (hoac chay gia lap neu dang bat che do Gia lap).
     */
    public synchronized boolean requestDispenseDishes(int quantity) {
        if (quantity <= 0) {
            return false;
        }
        String cmd = "XUATDIA_" + quantity;
        if (isConnected()) {
            return sendRawLine(cmd);
        }
        if (simulationMode) {
            notifyRawTraffic("TX (GIA LAP)", cmd);
            runSimulatedDispense(quantity);
            return true;
        }
        return false;
    }

    private void runSimulatedDispense(int quantity) {
        Thread simThread = new Thread(() -> {
            try {
                for (int i = 0; i < quantity; i++) {
                    Thread.sleep(1100L);
                    String simMsg = "XUATDIATRAICAY_OK_" + LocalTime.now().format(TIME_FORMATTER);
                    parseIncomingLine(simMsg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "esp32-sim-dispense");
        simThread.setDaemon(true);
        simThread.start();
    }

    private synchronized boolean sendRawLine(String command) {
        if (!isConnected()) {
            return false;
        }
        try {
            OutputStream out = comPort.getOutputStream();
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            notifyRawTraffic("TX", command);
            return true;
        } catch (IOException | NullPointerException e) {
            notifyRawTraffic("ERR", "Loi gui lenh xuong ESP32: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * Gia lap nhan mot ban tin tho tu ESP32 phuc vu nut test trong Admin.
     */
    public void simulateIncomingMessage(String rawLine) {
        if (rawLine != null && !rawLine.isBlank()) {
            parseIncomingLine(rawLine);
        }
    }

    /**
     * Phan tich tung dong du lieu nhan tu UART (dung .trim() cat bo \r va bat loi dinh dang).
     */
    public void parseIncomingLine(String rawLine) {
        if (rawLine == null) {
            return;
        }
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        notifyRawTraffic("RX", line);

        try {
            String[] parts = line.split("_");
            if (line.startsWith("XUATDIATRAICAY_OK") && parts.length >= 3) {
                String time = parts[2];
                for (Esp32MessageListener l : listeners) {
                    l.onDishDispensedSuccess(time);
                }
            } else if (line.startsWith("XUATDIATRAICAY_KET") && parts.length >= 3) {
                String time = parts[2];
                for (Esp32MessageListener l : listeners) {
                    l.onDishJammed(time);
                }
            } else if (line.startsWith("PHATHIEN_GIANLAN") && parts.length >= 3) {
                String time = parts[2];
                for (Esp32MessageListener l : listeners) {
                    l.onAbnormalDropAlert(time);
                }
            } else if (line.startsWith("MOITRUONG") && parts.length >= 4) {
                double temp = Double.parseDouble(parts[1]);
                double hum = Double.parseDouble(parts[2]);
                String time = parts[3];
                for (Esp32MessageListener l : listeners) {
                    l.onEnvironmentUpdate(time, temp, hum);
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            notifyRawTraffic("WARN", "Bo qua ban tin nhieu/sai dinh dang: " + line);
        }
    }

    private void notifyRawTraffic(String direction, String rawLine) {
        for (Esp32MessageListener l : listeners) {
            l.onRawSerialTraffic(direction, rawLine);
        }
    }
}
