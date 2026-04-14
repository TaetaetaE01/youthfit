package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.domain.model.PolicyDocument;

public record PolicyDocumentChunkResult(
        Long id,
        Long policyId,
        int chunkIndex,
        String content
) {

    public static PolicyDocumentChunkResult from(PolicyDocument policyDocument) {
        return new PolicyDocumentChunkResult(
                policyDocument.getId(),
                policyDocument.getPolicyId(),
                policyDocument.getChunkIndex(),
                policyDocument.getContent()
        );
    }
}
