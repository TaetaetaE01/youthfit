package com.youthfit.rag.application.dto.command;

import com.youthfit.policy.domain.model.PolicyEnrichment;

public record IndexPolicyDocumentCommand(Long policyId, String content, PolicyEnrichment enrichment) {

    /** enrichment 없이 생성하는 편의 생성자 — 기존 호출부 호환용 */
    public IndexPolicyDocumentCommand(Long policyId, String content) {
        this(policyId, content, null);
    }
}
