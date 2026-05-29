package com.youthfit.admin.application.dto;

import com.youthfit.policy.domain.model.AttachmentStatus;

/**
 * 정책 첨부 1건의 펼침 영역 상세 결과.
 *
 * <p>{@code policy_attachment} 의 한 행 + 해당 첨부 임베딩 적재 여부를 결합한다.
 */
public record AttachmentDetailResult(
        Long attachmentId,
        String filename,
        AttachmentStatus extractionStatus,
        boolean embedded
) {}
