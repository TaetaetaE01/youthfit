package com.youthfit.user.application.dto.command;

import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;

public record UpdateEligibilityProfileCommand(
        String legalDongCode,
        Integer age,
        MaritalStatus maritalStatus,
        Long incomeMin,
        Long incomeMax,
        Education education,
        EmploymentKind employmentKind,
        MajorField majorField,
        SpecializationField specializationField
) {
}
