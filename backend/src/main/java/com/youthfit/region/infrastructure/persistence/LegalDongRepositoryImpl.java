package com.youthfit.region.infrastructure.persistence;

import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.region.domain.repository.LegalDongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LegalDongRepositoryImpl implements LegalDongRepository {

    private final LegalDongJpaRepository jpaRepository;

    @Override
    public List<LegalDong> findByLevel(RegionLevel level) {
        return jpaRepository.findByLevelAndActiveTrueOrderBySidoNameAscSigunguNameAsc(level);
    }

    @Override
    public List<LegalDong> findByLevelAndParentCode(RegionLevel level, String parentCode) {
        return jpaRepository.findByLevelAndParentCodeAndActiveTrueOrderBySigunguNameAsc(level, parentCode);
    }

    @Override
    public Optional<LegalDong> findByCode(String code) {
        return jpaRepository.findById(code);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsById(code);
    }

    @Override
    public void saveAll(List<LegalDong> legalDongs) {
        jpaRepository.saveAll(legalDongs);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
