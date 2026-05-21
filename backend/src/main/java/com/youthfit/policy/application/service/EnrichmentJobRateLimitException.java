package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;

public class EnrichmentJobRateLimitException extends YouthFitException {
    public EnrichmentJobRateLimitException(Long policyId, int limitPerHour) {
        super(ErrorCode.ENRICHMENT_JOB_RATE_LIMITED,
                "Rate limit reached for policy " + policyId + " (max " + limitPerHour + "/hour)");
    }
}
