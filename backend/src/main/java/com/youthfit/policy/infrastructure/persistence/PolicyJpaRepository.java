package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyJpaRepository extends JpaRepository<Policy, Long>,
        JpaSpecificationExecutor<Policy> {

    /**
     * 어드민 enrichment 검토 필요 판정 SQL 조건절.
     * 다음 중 하나라도 충족하면 검토 대상:
     *   - enrichment 결측
     *   - status != 'OK'
     *   - confidence < 0.6
     *   - detail_level = 'LITE'
     *   - 핵심 섹션(supportTarget/supportContent/eligibilityCriteria) 2개 이상 누락
     * native @Query value 는 compile-time constant 여야 하므로 String 상수로 분리해 + 로 결합한다.
     */
    String NEEDS_REVIEW_PREDICATE = """
                p.enrichment IS NULL
             OR (p.enrichment->>'status') <> 'OK'
             OR ((p.enrichment->>'confidence')::numeric) < 0.6
             OR p.detail_level = 'LITE'
             OR (
                  (CASE WHEN COALESCE(p.enrichment->'sections'->>'supportTarget','') = '' THEN 1 ELSE 0 END)
                + (CASE WHEN COALESCE(p.enrichment->'sections'->>'supportContent','') = '' THEN 1 ELSE 0 END)
                + (CASE WHEN COALESCE(p.enrichment->'sections'->>'eligibilityCriteria','') = '' THEN 1 ELSE 0 END)
               ) >= 2
            """;

    List<Policy> findAllByStatus(PolicyStatus status);

    /**
     * 정규화 제목이 일치하면서 BOKJIRO_CENTRAL 출처가 등록된 Policy 를 찾는다.
     * 온통청년 ingestion 시점에 복지로 우선 중복 스킵 판단에 사용한다.
     */
    @Query("""
        SELECT p FROM Policy p
        WHERE p.normalizedTitle = :normalizedTitle
          AND EXISTS (
            SELECT 1 FROM PolicySource s
            WHERE s.policy = p
              AND s.sourceType = com.youthfit.policy.domain.model.SourceType.BOKJIRO_CENTRAL
          )
    """)
    Optional<Policy> findByNormalizedTitleWithBokjiroSource(@Param("normalizedTitle") String normalizedTitle);

    /**
     * 어드민 enrichment 검토 대상 후보 정책 페이지 조회.
     * needsReviewOnly = true 인 경우 {@link #NEEDS_REVIEW_PREDICATE} 적용.
     */
    @Query(value = "SELECT * FROM policy p"
                 + " WHERE (:needsReviewOnly = false OR (" + NEEDS_REVIEW_PREDICATE + "))"
                 + "   AND (:keyword IS NULL OR p.title ILIKE CONCAT('%', :keyword, '%'))",
           countQuery = "SELECT COUNT(*) FROM policy p"
                      + " WHERE (:needsReviewOnly = false OR (" + NEEDS_REVIEW_PREDICATE + "))"
                      + "   AND (:keyword IS NULL OR p.title ILIKE CONCAT('%', :keyword, '%'))",
           nativeQuery = true)
    Page<Policy> searchForEnrichmentReview(@Param("needsReviewOnly") boolean needsReviewOnly,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM policy", nativeQuery = true)
    long countTotal();

    @Query(value = "SELECT COUNT(*) FROM policy p WHERE " + NEEDS_REVIEW_PREDICATE, nativeQuery = true)
    long countNeedsReview();

    @Query(value = """
        SELECT p.enrichment->>'status' AS status, COUNT(*) AS cnt
          FROM policy p
         WHERE p.enrichment IS NOT NULL
         GROUP BY p.enrichment->>'status'
        """, nativeQuery = true)
    List<Object[]> aggregateStatusCounts();

    @Query(value = """
        SELECT p.detail_level AS level, COUNT(*) AS cnt
          FROM policy p
         GROUP BY p.detail_level
        """, nativeQuery = true)
    List<Object[]> aggregateDetailLevelCounts();
}
