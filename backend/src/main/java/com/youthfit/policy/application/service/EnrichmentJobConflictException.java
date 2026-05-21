package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;

public class EnrichmentJobConflictException extends YouthFitException {
    public EnrichmentJobConflictException(Long policyId) {
        super(ErrorCode.ENRICHMENT_JOB_CONFLICT,
                "EnrichmentJob already active for policy " + policyId);
    }
}
