package com.youthfit.eligibility.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.eligibility.presentation.dto.request.JudgeEligibilityRequest;
import com.youthfit.eligibility.presentation.dto.response.EligibilityJudgmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "적합도 판정", description = "사용자 프로필 기반 정책 적합도 판정 API")
public interface EligibilityApi {

    @Operation(summary = "적합도 판정", description = "사용자 프로필과 정책 기준을 비교하여 적합도를 판정한다")
    ResponseEntity<ApiResponse<EligibilityJudgmentResponse>> judge(
            Authentication authentication,
            JudgeEligibilityRequest request);
}
