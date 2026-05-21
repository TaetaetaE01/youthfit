package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
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

    List<Policy> findByCalendarRange(LocalDate from, LocalDate to,
                                      RegionFilter regionFilter, Category category);

    Page<Policy> findAlwaysOpen(RegionFilter regionFilter, Category category, Pageable pageable);

    Policy save(Policy policy);
}
