package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PolicyRepository {

    Optional<Policy> findById(Long id);

    Page<Policy> findAllByFilters(String regionCode, Category category, PolicyStatus status, Pageable pageable);

    Page<Policy> searchByKeyword(String keyword, Pageable pageable);

    Policy save(Policy policy);
}
