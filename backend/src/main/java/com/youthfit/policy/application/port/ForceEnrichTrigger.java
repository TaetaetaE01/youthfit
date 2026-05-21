package com.youthfit.policy.application.port;

import com.youthfit.policy.domain.model.PolicyReferenceSite;

import java.util.List;

public interface ForceEnrichTrigger {

    void forceEnrich(Long jobId, Long policyId, List<PolicyReferenceSite> urls);

    class EnrichmentTriggerException extends RuntimeException {
        public EnrichmentTriggerException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
