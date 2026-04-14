package com.youthfit.guide.infrastructure.persistence;

import com.youthfit.guide.domain.model.Guide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuideJpaRepository extends JpaRepository<Guide, Long> {

    Optional<Guide> findByPolicyId(Long policyId);
}
