package com.youthfit.policy.application.service;

public class EnrichmentJobRateLimitException extends RuntimeException {
    public EnrichmentJobRateLimitException(Long policyId, int limitPerHour) {
        super("Rate limit reached for policy " + policyId + " (max " + limitPerHour + "/hour)");
    }
}
