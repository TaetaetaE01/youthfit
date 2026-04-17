package com.youthfit.user.presentation.dto.response;

import com.youthfit.user.application.dto.result.EligibilityProfileResult;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

public record EligibilityProfileResponse(
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
    public static EligibilityProfileResponse from(EligibilityProfileResult result) {
        return new EligibilityProfileResponse(
                result.id(),
                result.userId(),
                result.legalDongCode(),
                result.sidoCode(),
                result.sidoName(),
                result.sigunguName(),
                result.age(),
                result.maritalStatus(),
                result.incomeMin(),
                result.incomeMax(),
                result.education(),
                result.employmentKind(),
                result.majorField(),
                result.specializationField()
        );
    }
}
