package com.youthfit.user.infrastructure.persistence;

import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EligibilityProfileRepositoryImpl implements EligibilityProfileRepository {

    private final EligibilityProfileJpaRepository jpaRepository;

    @Override
    public Optional<EligibilityProfile> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public EligibilityProfile save(EligibilityProfile profile) {
        return jpaRepository.save(profile);
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
