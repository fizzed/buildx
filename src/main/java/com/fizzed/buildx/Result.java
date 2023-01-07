package com.fizzed.buildx;

public class Result {

    private final long startMillis;
    private long endMillis;
    private ExecuteStatus status;
    private String message;

    public Result(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public Result setEndMillis(long endMillis) {
        this.endMillis = endMillis;
        return this;
    }

    public ExecuteStatus getStatus() {
        return status;
    }

    public Result setStatus(ExecuteStatus status) {
        this.status = status;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Result setMessage(String message) {
        this.message = message;
        return this;
    }

}