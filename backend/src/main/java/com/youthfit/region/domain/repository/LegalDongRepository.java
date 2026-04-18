package com.youthfit.region.domain.repository;

import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;

import java.util.List;
import java.util.Optional;

public interface LegalDongRepository {

    List<LegalDong> findByLevel(RegionLevel level);

    List<LegalDong> findByLevelAndParentCode(RegionLevel level, String parentCode);

    Optional<LegalDong> findByCode(String code);

    boolean existsByCode(String code);

    void saveAll(List<LegalDong> legalDongs);

    long count();
}
