package com.youthfit.user.domain.model;

public enum MaritalStatus implements LabeledEnum {
    MARRIED("기혼"),
    SINGLE("미혼");

    private final String displayName;

    MaritalStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
