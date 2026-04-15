package com.youthfit.guide.presentation.controller;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.common.response.ApiResponse;
import com.youthfit.guide.application.dto.result.GuideGenerationResult;
import com.youthfit.guide.application.dto.result.GuideResult;
import com.youthfit.guide.application.service.GuideGenerationService;
import com.youthfit.guide.presentation.dto.request.GenerateGuideRequest;
import com.youthfit.guide.presentation.dto.response.GuideGenerationResponse;
import com.youthfit.guide.presentation.dto.response.GuideResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GuideController implements GuideApi {

    private final GuideGenerationService guideGenerationService;

    @GetMapping("/api/v1/guides/{policyId}")
    @Override
    public ResponseEntity<ApiResponse<GuideResponse>> findGuide(
            @PathVariable Long policyId) {

        GuideResult result = guideGenerationService.findGuideByPolicyId(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND,
                        "해당 정책의 가이드가 존재하지 않습니다"));

        return ResponseEntity.ok(ApiResponse.ok(GuideResponse.from(result)));
    }

    @PostMapping("/api/internal/guides/generate")
    @Override
    public ResponseEntity<ApiResponse<GuideGenerationResponse>> generateGuide(
            @Valid @RequestBody GenerateGuideRequest request) {

        GuideGenerationResult result = guideGenerationService.generateGuide(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(GuideGenerationResponse.from(result)));
    }
}
