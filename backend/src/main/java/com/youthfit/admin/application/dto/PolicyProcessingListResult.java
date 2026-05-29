package com.youthfit.admin.application.dto;

import java.util.List;

/**
 * 정책 처리 현황 목록 Result.
 *
 * <p>{@code totalCount} 는 SQL 단계에서 검색·지역 필터만 적용된 모집단 크기이며,
 * {@code filteredItemCount} 는 페이지 결과에 대해 in-memory 빠른필터(완성도/단계별 FAILED 등)
 * 까지 적용한 뒤 남은 행 수다. computed 필터(RAG_FAILED, ATTACHMENT_EMBEDDING_MISSING,
 * GUIDE_RULE_FAILED, REFERENCE_FETCH_FAILED) 는 SQL 로 사전 거를 수 없어 페이지 후 후처리되므로
 * 같은 totalCount 안에서도 페이지마다 filteredItemCount 가 0 ~ {@code items.size()} 사이로 변동될 수 있다.</p>
 */
public record PolicyProcessingListResult(
    long totalCount,
    int page,
    int size,
    long filteredItemCount,
    List<PolicyProcessingItemResult> items
) {}
