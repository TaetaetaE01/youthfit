package com.youthfit.ingestion.application.dto.command;

import java.util.List;

/**
 * 첨부 임베딩 가치 판정 입력.
 * attachments 는 게이트 판정 대상(아직 미판정인 첨부)만 담는다.
 */
public record AttachmentEmbeddingJudgeCommand(
        String policyTitle,
        String policySummary,
        List<AttachmentItem> attachments
) {
    public record AttachmentItem(
            Long attachmentId,
            String name,
            String contentPreview
    ) {}
}
