package com.youthfit.eligibility.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.eligibility.application.dto.result.EligibilityJudgmentResult;
import com.youthfit.eligibility.application.service.EligibilityService;
import com.youthfit.eligibility.presentation.dto.request.JudgeEligibilityRequest;
import com.youthfit.eligibility.presentation.dto.response.EligibilityJudgmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eligibility")
@RequiredArgsConstructor
public class EligibilityController implements EligibilityApi {

    private final EligibilityService eligibilityService;

    @PostMapping("/judge")
    @Override
    public ResponseEntity<ApiResponse<EligibilityJudgmentResponse>> judge(
            Authentication authentication,
            @Valid @RequestBody JudgeEligibilityRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        EligibilityJudgmentResult result = eligibilityService.judgeEligibility(userId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(EligibilityJudgmentResponse.from(result)));
    }
}
