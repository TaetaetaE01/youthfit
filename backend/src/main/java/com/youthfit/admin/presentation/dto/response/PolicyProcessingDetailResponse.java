package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingDetailResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 정책 처리 현황 상세 응답 (목록 행 펼침 영역).
 *
 * <p>3 개 표 — 단계별/첨부별/참조 사이트별 — 의 데이터를 한 번에 내려보낸다.</p>
 */
@Schema(description = "정책 처리 현황 상세 (펼침 영역)")
public record PolicyProcessingDetailResponse(
        @Schema(description = "정책 ID") Long policyId,
        @Schema(description = "정책 제목") String title,
        @Schema(description = "처리 완성도") PolicyProcessingCompleteness completeness,
        @Schema(description = "5단계 처리 상세 (단계 순서)") List<StepDetailResponse> steps,
        @Schema(description = "정책 첨부 상세 목록") List<AttachmentDetailResponse> attachments,
        @Schema(description = "참조 사이트 fetch 상세 목록 (Phase D 이전엔 빈 리스트)")
        List<ReferenceDetailResponse> references
) {
    public static PolicyProcessingDetailResponse from(PolicyProcessingDetailResult r) {
        return new PolicyProcessingDetailResponse(
                r.policyId(),
                r.title(),
                r.completeness(),
                r.steps().stream().map(StepDetailResponse::from).toList(),
                r.attachments().stream().map(AttachmentDetailResponse::from).toList(),
                r.references().stream().map(ReferenceDetailResponse::from).toList()
        );
    }
}
