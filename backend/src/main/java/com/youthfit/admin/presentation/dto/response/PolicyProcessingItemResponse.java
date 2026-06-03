package com.youthfit.admin.presentation.dto.response;

import com.youthfit.admin.application.dto.PolicyProcessingItemResult;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정책 처리 현황 목록의 한 행.
 *
 * <p>도메인 enum 누수를 피하기 위해 stepStatuses 는 {@code Map<String, String>} 으로 평탄화한다.
 * key 는 {@link com.youthfit.policy.domain.model.ProcessingStep#name()},
 * value 는 {@link com.youthfit.policy.domain.model.ProcessingStatus#name()}.</p>
 */
@Schema(description = "정책 처리 현황 목록 행")
public record PolicyProcessingItemResponse(
        @Schema(description = "정책 ID") Long policyId,
        @Schema(description = "정책 제목") String title,
        @Schema(description = "지역명 (예: 서울)") String region,
        @Schema(description = "처리 완성도 (COMPLETE/PARTIAL/INCOMPLETE)")
        PolicyProcessingCompleteness completeness,
        @Schema(description = "5단계 처리 상태 맵. key=ProcessingStep name, value=ProcessingStatus name",
                example = "{\"INGESTION\":\"SUCCESS\",\"ENRICHMENT\":\"SUCCESS\",\"GUIDE\":\"SUCCESS\",\"RULE\":\"SUCCESS\",\"RAG_INDEXING\":\"SUCCESS\"}")
        Map<String, String> stepStatuses,
        @Schema(description = "첨부 집계") AttachmentSummaryResponse attachments,
        @Schema(description = "참조 사이트 집계") ReferenceSummaryResponse references,
        @Schema(description = "정책 출처 태그 목록 (여러 소스가 묶이면 전부)")
        List<SourceTagResponse> sources,
        @Schema(description = "처리 단계 최근 갱신 시각") LocalDateTime updatedAt
) {
    public static PolicyProcessingItemResponse from(PolicyProcessingItemResult r) {
        Map<String, String> stepStatuses = r.stepStatuses().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> e.getValue().name()
                ));
        return new PolicyProcessingItemResponse(
                r.policyId(),
                r.title(),
                r.region(),
                r.completeness(),
                stepStatuses,
                AttachmentSummaryResponse.from(r.attachments()),
                ReferenceSummaryResponse.from(r.references()),
                r.sources().stream().map(SourceTagResponse::from).toList(),
                r.updatedAt()
        );
    }
}
