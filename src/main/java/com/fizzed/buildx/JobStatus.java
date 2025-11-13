package com.fizzed.buildx;

public enum JobStatus {

    PENDING,
    RUNNING,
    // completed
    SUCCESS,
    SKIPPED,
    FAILED;

    public boolean isCompleted() {
        return this == SUCCESS || this == SKIPPED || this == FAILED;
    }

}