package com.youthfit.policy.domain.model;

public enum EnrichmentJobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
