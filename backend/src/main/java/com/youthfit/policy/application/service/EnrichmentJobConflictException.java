package com.youthfit.policy.application.service;

public class EnrichmentJobConflictException extends RuntimeException {
    public EnrichmentJobConflictException(Long policyId) {
        super("EnrichmentJob already active for policy " + policyId);
    }
}
