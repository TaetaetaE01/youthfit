package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.application.port.AdminEnrichmentSummaryReader;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * {@link AdminEnrichmentSummaryReader} 의 JPA 기반 구현.
 * {@link PolicyJpaRepository} 의 집계 쿼리 결과를 application DTO 로 매핑한다.
 */
@Component
public class AdminEnrichmentSummaryReaderImpl implements AdminEnrichmentSummaryReader {

    private final PolicyJpaRepository jpaRepository;

    public AdminEnrichmentSummaryReaderImpl(PolicyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EnrichmentReviewSummaryResult readSummary() {
        long total = jpaRepository.countTotal();
        long needsReview = jpaRepository.countNeedsReview();

        Map<EnrichmentStatus, Long> byStatus = new EnumMap<>(EnrichmentStatus.class);
        for (Object[] row : jpaRepository.aggregateStatusCounts()) {
            String key = (String) row[0];
            if (key == null) {
                continue;
            }
            try {
                byStatus.put(EnrichmentStatus.valueOf(key), ((Number) row[1]).longValue());
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 status 값은 집계에서 제외
            }
        }

        Map<DetailLevel, Long> byDetailLevel = new EnumMap<>(DetailLevel.class);
        for (Object[] row : jpaRepository.aggregateDetailLevelCounts()) {
            String key = (String) row[0];
            if (key == null) {
                continue;
            }
            try {
                byDetailLevel.put(DetailLevel.valueOf(key), ((Number) row[1]).longValue());
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 detail_level 값은 집계에서 제외
            }
        }

        return new EnrichmentReviewSummaryResult(total, needsReview, byStatus, byDetailLevel);
    }
}
