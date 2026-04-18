package com.youthfit.region.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.region.application.dto.result.RegionResult;
import com.youthfit.region.domain.model.LegalDong;
import com.youthfit.region.domain.model.RegionLevel;
import com.youthfit.region.domain.repository.LegalDongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionQueryService {

    private final LegalDongRepository legalDongRepository;

    public Optional<RegionResult> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return legalDongRepository.findByCode(code).map(RegionResult::from);
    }

    public List<RegionResult> findRegions(RegionLevel level, String parentCode) {
        if (level == null) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "level은 필수입니다");
        }
        if (level == RegionLevel.SIGUNGU && (parentCode == null || parentCode.isBlank())) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "시군구 조회 시 parentCode는 필수입니다");
        }

        List<LegalDong> source = level == RegionLevel.SIDO
                ? legalDongRepository.findByLevel(level)
                : legalDongRepository.findByLevelAndParentCode(level, parentCode);

        return source.stream().map(RegionResult::from).toList();
    }
}
