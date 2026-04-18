package com.youthfit.user.application.dto.result;

import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EligibilityProfile;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

public record EligibilityProfileResult(
        Long id,
        Long userId,
        String legalDongCode,
        String sidoCode,
        String sidoName,
        String sigunguName,
        Integer age,
        MaritalStatus maritalStatus,
        Long incomeMin,
        Long incomeMax,
        Education education,
        EmploymentKind employmentKind,
        MajorField majorField,
        SpecializationField specializationField
) {
    public static EligibilityProfileResult from(EligibilityProfile profile, String sidoName, String sigunguName) {
        String code = profile.getLegalDongCode();
        String sidoCode = code != null && code.length() >= 2
                ? code.substring(0, 2) + "00000000"
                : null;
        return new EligibilityProfileResult(
                profile.getId(),
                profile.getUserId(),
                code,
                sidoCode,
                sidoName,
                sigunguName,
                profile.getAge(),
                profile.getMaritalStatus(),
                profile.getIncomeMin(),
                profile.getIncomeMax(),
                profile.getEducation(),
                profile.getEmploymentKind(),
                profile.getMajorField(),
                profile.getSpecializationField()
        );
    }
}
