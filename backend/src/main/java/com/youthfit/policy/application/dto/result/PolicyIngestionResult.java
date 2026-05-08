package com.youthfit.policy.application.dto.result;

public record PolicyIngestionResult(Long policyId, Outcome outcome) {

    public enum Outcome {
        REGISTERED,
        UPDATED,
        SKIPPED_DUPLICATE
    }

    public boolean isNew() {
        return outcome == Outcome.REGISTERED;
    }

    public static PolicyIngestionResult registered(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.REGISTERED);
    }

    public static PolicyIngestionResult updated(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.UPDATED);
    }

    public static PolicyIngestionResult skippedDuplicate(Long policyId) {
        return new PolicyIngestionResult(policyId, Outcome.SKIPPED_DUPLICATE);
    }
}
