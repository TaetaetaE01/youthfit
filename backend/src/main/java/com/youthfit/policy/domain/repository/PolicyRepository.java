package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    List<Policy> findByCalendarRange(LocalDate from, LocalDate to,
                                      RegionFilter regionFilter, Category category);

    Page<Policy> findAlwaysOpen(RegionFilter regionFilter, Category category, Pageable pageable);

    Policy save(Policy policy);

    /**
     * 어드민 enrichment 검토 대상 Policy를 검색한다.
     * needsReviewOnly = true 인 경우 enrichment 상태/신뢰도/detailLevel/핵심 섹션 누락 기준으로 필터링한다.
     * statuses / detailLevels 가 null 또는 빈 컬렉션이면 해당 필터를 적용하지 않는다.
     * application/presentation 레이어 의존을 차단하기 위해 primitive 및 문자열 컬렉션만 받는다.
     */
    Page<Policy> searchForEnrichmentReview(boolean needsReviewOnly,
                                           String keyword,
                                           Set<String> statuses,
                                           Set<String> detailLevels,
                                           Pageable pageable);

    /**
     * 어드민 정책 처리 현황 대시보드 목록 조회.
     *
     * <p>제목 부분 일치 검색(query) 및 단일 region code 필터를 적용하며,
     * 정렬은 호출자가 제공한 Sort 를 그대로 사용한다. query / region 이 null 이면 해당 조건을 무시한다.
     *
     * @param query  제목 부분 일치 검색어 (null 허용)
     * @param region 정확히 일치할 region code (null 허용)
     * @param sort   페이지 내 정렬 — 호출자가 책임지고 Policy 엔티티 필드명을 사용해야 한다.
     * @param pageable 페이지·사이즈 정보 (Sort 는 무시되고 위의 sort 가 적용된다)
     * @return Policy 페이지
     */
    Page<Policy> findForAdminProcessing(String query, String region, Sort sort, Pageable pageable);

    /**
     * 어드민 정책 처리 현황 KPI 집계용 전체 정책 조회.
     *
     * <p>총건수/완성도 버킷/최근 24시간 신규 정책 집계를 위해 모든 정책을 한 번에 로드한다.
     * 정책 수가 수백~수천 수준이라는 전제(MVP 운영 데이터 기준)에서 안전하며,
     * 정책 수가 그 이상으로 늘어나면 SQL group-by 집계로 마이그레이션해야 한다.
     */
    List<Policy> findAllForStats();
}
