package com.youthfit.user.presentation.dto.request;

import com.youthfit.user.application.dto.command.UpdateEligibilityProfileCommand;
import com.youthfit.user.domain.model.Education;
import com.youthfit.user.domain.model.EmploymentKind;
import com.youthfit.user.domain.model.MajorField;
import com.youthfit.user.domain.model.MaritalStatus;
import com.youthfit.user.domain.model.SpecializationField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateEligibilityProfileRequest(
        @Pattern(regexp = "^\\d{10}$", message = "법정동코드는 10자리 숫자여야 합니다")
        String legalDongCode,

        @Min(value = 0, message = "나이는 0 이상이어야 합니다")
        @Max(value = 150, message = "나이는 150 이하여야 합니다")
        Integer age,

        MaritalStatus maritalStatus,

        @PositiveOrZero(message = "소득 최솟값은 0 이상이어야 합니다")
        Long incomeMin,

        @PositiveOrZero(message = "소득 최댓값은 0 이상이어야 합니다")
        Long incomeMax,

        Education education,

        EmploymentKind employmentKind,

        MajorField majorField,

        SpecializationField specializationField
) {
    public UpdateEligibilityProfileCommand toCommand() {
        return new UpdateEligibilityProfileCommand(
                legalDongCode,
                age,
                maritalStatus,
                incomeMin,
                incomeMax,
                education,
                employmentKind,
                majorField,
                specializationField
        );
    }
}
