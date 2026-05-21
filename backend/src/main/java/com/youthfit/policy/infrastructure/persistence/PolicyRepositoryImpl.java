package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public Page<Policy> searchForEnrichmentReview(boolean needsReviewOnly, String keyword, Pageable pageable) {
        return jpaRepository.searchForEnrichmentReview(needsReviewOnly, normalizeKeyword(keyword), pageable);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
