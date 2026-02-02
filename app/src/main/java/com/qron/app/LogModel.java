package com.qron.app;

public class LogModel {
    private String email;
    private String action;
    private String details;
    private long timestamp;

    public LogModel() {}

    public LogModel(String email, String action, String details, long timestamp) {
        this.email = email;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    public String getEmail() { return email; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }
}
