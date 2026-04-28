package com.youthfit.policy.domain.model;

public enum AttachmentStatus {
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    EXTRACTING,
    EXTRACTED,
    FAILED,
    SKIPPED;

    public boolean isTerminal() {
        return this == EXTRACTED || this == SKIPPED;
    }

    public boolean isInFlight() {
        return this == DOWNLOADING || this == EXTRACTING;
    }
}
