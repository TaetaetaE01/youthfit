package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicySourceJpaRepository extends JpaRepository<PolicySource, Long> {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyIdOrderByIdAsc(Long policyId);

    List<PolicySource> findAllByPolicyIdInOrderByIdAsc(List<Long> policyIds);

    @Query("""
           select s.externalId, s.sourceHash
           from PolicySource s
           where s.sourceType = :sourceType
           """)
    List<Object[]> findExternalIdAndHashBySourceType(@Param("sourceType") SourceType sourceType);
}
