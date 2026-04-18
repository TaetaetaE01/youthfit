package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.EligibilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EligibilityProfileJpaRepository extends JpaRepository<EligibilityProfile, Long> {

    Optional<EligibilityProfile> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
