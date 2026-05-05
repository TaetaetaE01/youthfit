package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDetailResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupKpiResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupSummaryResponse;
import com.youthfit.common.response.ApiResponse;
import com.youthfit.qna.domain.model.LookupResultType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Admin - Q&A Cache Lookup Log", description = "Q&A 캐시 hit/miss 로그 조회 (ROLE_ADMIN 필수)")
public interface AdminQnaCacheApi {

    @Operation(summary = "KPI 조회",
            description = "오늘/어제 hit률, 7일 평균 유사도 및 비용 절감 추정")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<QnaCacheLookupKpiResponse>> kpi();

    @Operation(summary = "일자별 통계",
            description = "지정된 기간 내 날짜별 hit/miss 통계 및 평균 유사도")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<List<QnaCacheLookupDailyStatsResponse>>> dailyStats(
            @Parameter(description = "조회 일수 (기본 14)") @RequestParam(defaultValue = "14") int days);

    @Operation(summary = "lookup 로그 목록 (필터 + 페이지네이션)",
            description = "결과 타입, 정책, 시간 범위로 필터링 가능한 페이지네이션 목록")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<Page<QnaCacheLookupSummaryResponse>>> list(
            @Parameter(description = "결과 필터 (HIT | MISS)") @RequestParam(required = false) LookupResultType result,
            @Parameter(description = "정책 ID 필터") @RequestParam(required = false) Long policyId,
            @Parameter(description = "시작 시각 (기본: 없음)") @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "종료 시각 (기본: 없음)") @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본: 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본: 20, 최대: 100)") @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "lookup 로그 상세",
            description = "ID로 단건 lookup 로그의 상세 정보를 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "로그 없음")
    })
    ResponseEntity<ApiResponse<QnaCacheLookupDetailResponse>> detail(
            @Parameter(description = "lookup 로그 ID") @PathVariable Long id);

    @Operation(summary = "필터 결과 CSV export",
            description = "지정된 필터 조건으로 조회된 lookup 로그를 CSV 형식으로 다운로드")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CSV 다운로드 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    void exportCsv(
            @Parameter(description = "결과 필터 (HIT | MISS)") @RequestParam(required = false) LookupResultType result,
            @Parameter(description = "정책 ID 필터") @RequestParam(required = false) Long policyId,
            @Parameter(description = "시작 시각") @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "종료 시각") @RequestParam(required = false) LocalDateTime to,
            HttpServletResponse response);
}
