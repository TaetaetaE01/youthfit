package com.youthfit.ingestion.application.dto.result;

import java.util.List;
import java.util.Optional;

/**
 * 첨부 임베딩 가치 판정 결과. decisions 는 첨부별 포함 여부.
 */
public record AttachmentEmbeddingResult(
        List<AttachmentDecision> decisions
) {
    public record AttachmentDecision(
            Long attachmentId,
            boolean embed,
            String reason
    ) {}

    public Optional<AttachmentDecision> findByAttachmentId(Long attachmentId) {
        return decisions.stream()
                .filter(d -> attachmentId.equals(d.attachmentId()))
                .findFirst();
    }
}
