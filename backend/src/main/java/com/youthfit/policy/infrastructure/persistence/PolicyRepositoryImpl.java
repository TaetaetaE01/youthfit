package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.application.dto.EnrichmentReviewFilter;
import com.youthfit.policy.application.dto.EnrichmentReviewSummary;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PolicyRepositoryImpl implements PolicyRepository {

    private final PolicyJpaRepository jpaRepository;

    public PolicyRepositoryImpl(PolicyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Policy> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<Policy> findAllByFilters(RegionFilter regionFilter, Category category, PolicyStatus status,
                                         Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withFilters(regionFilter, category, status), pageable);
    }

    @Override
    public Page<Policy> searchByKeyword(String keyword, PolicyStatus status, Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withKeyword(keyword, status), pageable);
    }

    @Override
    public List<Policy> findAllByStatus(PolicyStatus status) {
        return jpaRepository.findAllByStatus(status);
    }

    @Override
    public List<Policy> findAllById(Iterable<Long> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public Optional<Policy> findByNormalizedTitleWithBokjiroSource(String normalizedTitle) {
        return jpaRepository.findByNormalizedTitleWithBokjiroSource(normalizedTitle);
    }

    @Override
    public Policy save(Policy policy) {
        return jpaRepository.save(policy);
    }

    @Override
    public Page<Policy> searchForEnrichmentReview(EnrichmentReviewFilter filter, Pageable pageable) {
        boolean needsReviewOnly = filter != null && Boolean.TRUE.equals(filter.needsReviewOnly());
        String keyword = filter == null ? null : normalizeKeyword(filter.keyword());
        return jpaRepository.searchForEnrichmentReview(needsReviewOnly, keyword, pageable);
    }

    @Override
    public EnrichmentReviewSummary summarizeEnrichmentReview() {
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

        return new EnrichmentReviewSummary(total, needsReview, byStatus, byDetailLevel);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
