package com.youthfit.rag.application.dto.result;

import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.model.SimilarChunk;

public record PolicyDocumentChunkResult(
        Long id,
        Long policyId,
        int chunkIndex,
        String content,
        double distance,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd
) {

    public static PolicyDocumentChunkResult from(PolicyDocument policyDocument) {
        return new PolicyDocumentChunkResult(
                policyDocument.getId(),
                policyDocument.getPolicyId(),
                policyDocument.getChunkIndex(),
                policyDocument.getContent(),
                0.0,
                policyDocument.getAttachmentId(),
                policyDocument.getPageStart(),
                policyDocument.getPageEnd()
        );
    }

    public static PolicyDocumentChunkResult from(SimilarChunk chunk) {
        return new PolicyDocumentChunkResult(
                chunk.id(),
                chunk.policyId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.distance(),
                chunk.attachmentId(),
                chunk.pageStart(),
                chunk.pageEnd()
        );
    }
}
