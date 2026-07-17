package com.osir.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskState {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled");

    private final String value;

    TaskState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
