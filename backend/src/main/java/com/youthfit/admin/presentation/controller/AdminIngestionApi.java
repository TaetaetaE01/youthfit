package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.IngestionDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureDetailResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionKpiResponse;
import com.youthfit.admin.presentation.dto.response.IngestionRetryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionSourceSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionStaleSourceResponse;
import com.youthfit.ingestion.domain.model.FailureReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@Tag(name = "Admin Ingestion Health", description = "어드민 — Ingestion 신선도/실패 (Spec 5)")
public interface AdminIngestionApi {

    @Operation(summary = "Ingestion KPI (어제 신규/실패 + 7일 평균)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<IngestionKpiResponse> getKpi();

    @Operation(summary = "일자별·source 별 stacked bar 통계")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<List<IngestionDailyStatsResponse>> getDailyStats(
            @Parameter(description = "조회 일수, 기본 14")
            @RequestParam(required = false, defaultValue = "14") int days);

    @Operation(summary = "원천별 마지막 수신/7일 합계/실패율")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<List<IngestionSourceSummaryResponse>> getSourceSummaries();

    @Operation(summary = "24h 미수신 source 알람")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<List<IngestionStaleSourceResponse>> getStaleSources();

    @Operation(summary = "실패 항목 리스트 (필터 + 페이지네이션)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<Page<IngestionFailureSummaryResponse>> searchFailures(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) FailureReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size);

    @Operation(summary = "실패 상세 (raw_payload 포함)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)"),
            @ApiResponse(responseCode = "404", description = "항목 없음 (YF-004)")
    })
    ResponseEntity<IngestionFailureDetailResponse> getFailureDetail(
            @Parameter(description = "실패 항목 id") @PathVariable Long id);

    @Operation(summary = "실패 항목 재처리 (단건)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "재처리 결과"),
            @ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<IngestionRetryResponse> retryFailure(@PathVariable Long id);
}
