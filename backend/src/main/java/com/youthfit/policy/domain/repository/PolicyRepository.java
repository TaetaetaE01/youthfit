package com.youthfit.policy.domain.repository;

import com.youthfit.policy.application.dto.EnrichmentReviewFilter;
import com.youthfit.policy.application.dto.EnrichmentReviewSummary;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository {

    Optional<Policy> findById(Long id);

    Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                                   Pageable pageable);

    Page<Policy> searchByKeyword(String keyword, PolicyStatus status, Pageable pageable);

    List<Policy> findAllByStatus(PolicyStatus status);

    List<Policy> findAllById(Iterable<Long> ids);

    /**
     * 정규화 제목이 일치하면서 BOKJIRO_CENTRAL 출처가 등록된 Policy 를 찾는다.
     * 온통청년 ingestion 시점에 복지로 우선 중복 스킵 판단에 사용한다.
     */
    Optional<Policy> findByNormalizedTitleWithBokjiroSource(String normalizedTitle);

    Policy save(Policy policy);

    /**
     * 어드민 enrichment 검토 대상 Policy를 검색한다.
     * needsReviewOnly = true 인 경우 enrichment 상태/신뢰도/detailLevel/핵심 섹션 누락 기준으로 필터링한다.
     */
    Page<Policy> searchForEnrichmentReview(EnrichmentReviewFilter filter, Pageable pageable);

    /**
     * 어드민 enrichment 검토 현황 요약(전체/검토필요/상태별/detailLevel별 카운트)을 반환한다.
     */
    EnrichmentReviewSummary summarizeEnrichmentReview();
}
