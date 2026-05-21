package com.youthfit.policy.application.port;

import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;

/**
 * 어드민 enrichment 검토 현황 요약(전체/검토필요/상태별/detailLevel별 카운트) 조회 포트.
 * 도메인 리포지토리 인터페이스에 application DTO 의존이 묶이지 않도록 분리한다.
 * 구현체는 infrastructure 레이어에서 제공한다.
 */
public interface AdminEnrichmentSummaryReader {

    EnrichmentReviewSummaryResult readSummary();
}
