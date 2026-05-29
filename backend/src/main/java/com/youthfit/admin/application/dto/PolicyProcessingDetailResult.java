package com.youthfit.admin.application.dto;

import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;

import java.util.List;

/**
 * 정책 처리 현황 펼침 영역 상세 결과.
 *
 * <p>단계 / 첨부 / 참조 사이트 3개 표의 데이터를 한 번에 묶어 반환한다.
 */
public record PolicyProcessingDetailResult(
        Long policyId,
        String title,
        PolicyProcessingCompleteness completeness,
        List<StepDetailResult> steps,
        List<AttachmentDetailResult> attachments,
        List<ReferenceDetailResult> references
) {}
