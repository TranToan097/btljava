package com.bantraicay.model;

/**
 * Thuc the anh xa bang realtime_event_logs luu nhat ky su kien thoi gian thuc phuc vu doi soat.
 */
public class RealtimeEventLog {

    private long logId;
    private String espTimestamp;
    private String systemTimestamp;
    private String eventType;
    private String orderId;
    private String rawMessage;
    private String note;

    public RealtimeEventLog() {
    }

    public RealtimeEventLog(String systemTimestamp, String espTimestamp, String eventType,
                            String orderId, String rawMessage, String note) {
        this.systemTimestamp = systemTimestamp;
        this.espTimestamp = espTimestamp;
        this.eventType = eventType;
        this.orderId = orderId;
        this.rawMessage = rawMessage;
        this.note = note;
    }

    public RealtimeEventLog(long logId, String systemTimestamp, String espTimestamp, String eventType,
                            String orderId, String rawMessage, String note) {
        this(systemTimestamp, espTimestamp, eventType, orderId, rawMessage, note);
        this.logId = logId;
    }

    public long getLogId() {
        return logId;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public String getEspTimestamp() {
        return espTimestamp;
    }

    public void setEspTimestamp(String espTimestamp) {
        this.espTimestamp = espTimestamp;
    }

    public String getSystemTimestamp() {
        return systemTimestamp;
    }

    public void setSystemTimestamp(String systemTimestamp) {
        this.systemTimestamp = systemTimestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
