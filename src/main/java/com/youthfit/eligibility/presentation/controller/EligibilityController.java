package com.youthfit.eligibility.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.presentation.dto.request.JudgeEligibilityRequest;
import com.youthfit.eligibility.presentation.dto.response.EligibilityJudgmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "적합도 판정", description = "사용자 프로필 기반 정책 적합도 판정 API")
@RestController
@RequestMapping("/api/v1/eligibility")
@RequiredArgsConstructor
public class EligibilityController {

    private final EligibilityService eligibilityService;

    @Operation(summary = "적합도 판정", description = "사용자 프로필과 정책 기준을 비교하여 적합도를 판정한다")
    @PostMapping("/judge")
    public ResponseEntity<ApiResponse<EligibilityJudgmentResponse>> judge(
            Authentication authentication,
            @Valid @RequestBody JudgeEligibilityRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        EligibilityJudgmentResult result = eligibilityService.judgeEligibility(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(EligibilityJudgmentResponse.from(result)));
    }
}
