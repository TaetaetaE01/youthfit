package com.youthfit.region.infrastructure.persistence;

import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalDongJpaRepository extends JpaRepository<LegalDong, String> {

    List<LegalDong> findByLevelAndActiveTrueOrderBySidoNameAscSigunguNameAsc(RegionLevel level);

    List<LegalDong> findByLevelAndParentCodeAndActiveTrueOrderBySigunguNameAsc(
            RegionLevel level, String parentCode);
}
