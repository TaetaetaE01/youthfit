package com.youthfit.user.domain.repository;

import com.youthfit.user.domain.model.EligibilityProfile;

import java.util.Optional;

public interface EligibilityProfileRepository {

    Optional<EligibilityProfile> findByUserId(Long userId);

    EligibilityProfile save(EligibilityProfile profile);

    void deleteByUserId(Long userId);
}
