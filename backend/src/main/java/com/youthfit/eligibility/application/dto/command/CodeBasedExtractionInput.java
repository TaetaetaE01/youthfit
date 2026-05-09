package com.youthfit.eligibility.application.dto.command;

import java.util.List;

public record CodeBasedExtractionInput(
        Integer ageMin,
        Integer ageMax,
        String ageLimitYn,
        String maritalStatusCd,
        String earnConditionCd,
        Integer earnMin,
        Integer earnMax,
        String earnEtcCn,
        String employmentKindCd,
        String educationCd,
        String majorFieldCd,
        String specializationCd,
        List<String> zipCodes
) {}
