package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Admin LLM Cost", description = "어드민 — LLM 호출/비용 대시보드 (Spec 4)")
public interface AdminLlmCostApi {

    @Operation(summary = "오늘/이번주/이번달 비용 KPI")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<LlmCostKpiResponse> getKpi();

    @Operation(summary = "시간별 비용 시계열 (모듈별 line 차트용)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<LlmCostSeriesResponse> getSeries(
            @Parameter(description = "24h | 7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);

    @Operation(summary = "일자별·모듈별 비용 (stacked bar 용)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<List<LlmCostModuleDailyResponse>> getDailyByModule(
            @Parameter(description = "7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);

    @Operation(summary = "모델별 호출/토큰/비용 합계 (테이블용)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<List<LlmCostModelSummaryResponse>> getModelSummary(
            @Parameter(description = "7d | 30d (기본 7d)")
            @RequestParam(required = false, defaultValue = "7d") String range);
}
