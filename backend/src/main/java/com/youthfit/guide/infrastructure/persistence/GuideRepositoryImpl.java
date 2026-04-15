package com.youthfit.guide.infrastructure.persistence;

import com.youthfit.guide.domain.model.Guide;
import com.youthfit.guide.domain.repository.GuideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GuideRepositoryImpl implements GuideRepository {

    private final GuideJpaRepository jpaRepository;

    @Override
    public Guide save(Guide guide) {
        return jpaRepository.save(guide);
    }

    @Override
    public Optional<Guide> findByPolicyId(Long policyId) {
        return jpaRepository.findByPolicyId(policyId);
    }
}
