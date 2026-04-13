package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyJpaRepository extends JpaRepository<Policy, Long>,
        JpaSpecificationExecutor<Policy> {

    @Query("SELECT p FROM Policy p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Policy> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
