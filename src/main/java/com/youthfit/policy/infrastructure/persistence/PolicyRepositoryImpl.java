package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
    public Page<Policy> findAllByFilters(String regionCode, Category category,
                                         PolicyStatus status, Pageable pageable) {
        return jpaRepository.findAll(
                PolicySpecification.withFilters(regionCode, category, status), pageable);
    }

    @Override
    public Page<Policy> searchByKeyword(String keyword, Pageable pageable) {
        return jpaRepository.searchByKeyword(keyword, pageable);
    }

    @Override
    public Policy save(Policy policy) {
        return jpaRepository.save(policy);
    }
}
