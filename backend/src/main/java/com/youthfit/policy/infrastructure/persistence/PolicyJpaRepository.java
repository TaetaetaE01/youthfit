package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyJpaRepository extends JpaRepository<Policy, Long>,
        JpaSpecificationExecutor<Policy> {

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
}
