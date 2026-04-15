package com.youthfit.guide.domain.repository;

import com.youthfit.guide.domain.model.Guide;

import java.util.Optional;

public interface GuideRepository {

    Guide save(Guide guide);

    Optional<Guide> findByPolicyId(Long policyId);
}
