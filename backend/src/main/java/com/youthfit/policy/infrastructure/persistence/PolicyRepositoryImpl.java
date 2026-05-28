package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
                                         SourceType source, Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withFilters(regionFilter, category, status, source), pageable);
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
    public List<Policy> findByCalendarRange(LocalDate from, LocalDate to,
                                             RegionFilter regionFilter, Category category) {
        return jpaRepository.findAll(
                PolicySpecification.withCalendarRange(from, to, regionFilter, category));
    }

    @Override
    public Page<Policy> findAlwaysOpen(RegionFilter regionFilter, Category category, Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.alwaysOpen(regionFilter, category), pageable);
    }

    @Override
    public Policy save(Policy policy) {
        return jpaRepository.save(policy);
    }

    @Override
    public Page<Policy> searchForEnrichmentReview(boolean needsReviewOnly,
                                                  String keyword,
                                                  Set<String> statuses,
                                                  Set<String> detailLevels,
                                                  Pageable pageable) {
        boolean allStatuses = statuses == null || statuses.isEmpty();
        boolean allDetailLevels = detailLevels == null || detailLevels.isEmpty();
        // JPA 네이티브 쿼리 IN 절은 빈 컬렉션 바인딩에 취약하므로, 필터 미적용 시 매칭되지 않는 sentinel을 사용한다.
        // 실제로는 :allStatuses / :allDetailLevels 플래그가 true이므로 IN 절은 평가되지 않는다.
        Set<String> effectiveStatuses = allStatuses ? Set.of("__NONE__") : statuses;
        Set<String> effectiveDetailLevels = allDetailLevels ? Set.of("__NONE__") : detailLevels;
        return jpaRepository.searchForEnrichmentReview(
                needsReviewOnly,
                normalizeKeyword(keyword),
                allStatuses,
                effectiveStatuses,
                allDetailLevels,
                effectiveDetailLevels,
                pageable);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
