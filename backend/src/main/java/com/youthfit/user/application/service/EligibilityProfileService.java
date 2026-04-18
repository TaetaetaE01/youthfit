package com.youthfit.user.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.region.application.dto.result.RegionResult;
import com.youthfit.region.application.service.RegionQueryService;
import com.youthfit.user.application.dto.command.UpdateEligibilityProfileCommand;
import com.youthfit.user.application.dto.result.EligibilityProfileResult;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.repository.EligibilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EligibilityProfileService {

    private final EligibilityProfileRepository eligibilityProfileRepository;
    private final RegionQueryService regionQueryService;

    @Transactional(readOnly = true)
    public EligibilityProfileResult findMyProfile(Long userId) {
        EligibilityProfile profile = eligibilityProfileRepository.findByUserId(userId)
                .orElseGet(() -> EligibilityProfile.empty(userId));
        return toResult(profile);
    }

    @Transactional
    public EligibilityProfileResult updateMyProfile(Long userId, UpdateEligibilityProfileCommand command) {
        EligibilityProfile profile = eligibilityProfileRepository.findByUserId(userId)
                .orElseGet(() -> eligibilityProfileRepository.save(EligibilityProfile.empty(userId)));

        if (command.legalDongCode() != null) {
            validateLegalDongCode(command.legalDongCode());
            profile.changeLegalDongCode(command.legalDongCode());
        }
        if (command.age() != null) {
            profile.changeAge(command.age());
        }
        if (command.maritalStatus() != null) {
            profile.changeMaritalStatus(command.maritalStatus());
        }
        if (command.incomeMin() != null || command.incomeMax() != null) {
            Long min = command.incomeMin() != null ? command.incomeMin() : profile.getIncomeMin();
            Long max = command.incomeMax() != null ? command.incomeMax() : profile.getIncomeMax();
            profile.changeIncomeRange(min, max);
        }
        if (command.education() != null) {
            profile.changeEducation(command.education());
        }
        if (command.employmentKind() != null) {
            profile.changeEmploymentKind(command.employmentKind());
        }
        if (command.majorField() != null) {
            profile.changeMajorField(command.majorField());
        }
        if (command.specializationField() != null) {
            profile.changeSpecializationField(command.specializationField());
        }

        return toResult(profile);
    }

    private void validateLegalDongCode(String code) {
        if (regionQueryService.findByCode(code).isEmpty()) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "존재하지 않는 법정동코드입니다: " + code);
        }
    }

    private EligibilityProfileResult toResult(EligibilityProfile profile) {
        String code = profile.getLegalDongCode();
        if (code == null || code.isBlank()) {
            return EligibilityProfileResult.from(profile, null, null);
        }
        String sidoCode = code.substring(0, 2) + "00000000";
        String sidoName = regionQueryService.findByCode(sidoCode)
                .map(RegionResult::name)
                .orElse(null);
        String sigunguName = code.equals(sidoCode) ? null
                : regionQueryService.findByCode(code)
                        .map(RegionResult::name)
                        .orElse(null);
        return EligibilityProfileResult.from(profile, sidoName, sigunguName);
    }
}
