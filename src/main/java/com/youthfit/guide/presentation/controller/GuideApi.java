package com.youthfit.guide.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.guide.presentation.dto.request.GenerateGuideRequest;
import com.youthfit.guide.presentation.dto.response.GuideGenerationResponse;
import com.youthfit.guide.presentation.dto.response.GuideResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "가이드", description = "정책 가이드 조회 및 생성 API")
public interface GuideApi {

    @Operation(summary = "정책 가이드 조회", description = "특정 정책의 구조화된 가이드를 조회한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)")
    })
    ResponseEntity<ApiResponse<GuideResponse>> findGuide(
            @Parameter(description = "정책 ID") Long policyId
    );

    @Operation(summary = "정책 가이드 생성", description = "정책 원문 기반으로 구조화된 가이드를 생성한다 (내부 API)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 요청 처리 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<GuideGenerationResponse>> generateGuide(GenerateGuideRequest request);
}
