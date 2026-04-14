package com.youthfit.eligibility.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.eligibility.presentation.dto.request.JudgeEligibilityRequest;
import com.youthfit.eligibility.presentation.dto.response.EligibilityJudgmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "적합도 판정", description = "사용자 프로필 기반 정책 적합도 판정 API")
public interface EligibilityApi {

    @Operation(summary = "적합도 판정", description = "사용자 프로필과 정책 기준을 비교하여 적합도를 판정한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "판정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정책을 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<EligibilityJudgmentResponse>> judge(
            Authentication authentication,
            JudgeEligibilityRequest request);
}
