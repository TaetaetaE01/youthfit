package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.EmailAttemptDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptDetailResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptKpiResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptPreviewResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptSummaryResponse;
import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.domain.model.NotificationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin - Email", description = "이메일 발송 시도 추적 API (ROLE_ADMIN 필수)")
public interface AdminEmailLogApi {

    @Operation(summary = "이메일 발송 시도 목록 조회 (페이지네이션, 필터)",
            description = "기간·상태·유형·수신자로 필터링 가능한 페이지네이션 목록")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<ApiResponse<Page<EmailAttemptSummaryResponse>>> list(
            @Parameter(description = "시작일 (yyyy-MM-dd, 기본: 30일 전)") LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd, 기본: 오늘)") LocalDate to,
            @Parameter(description = "상태 CSV (예: FAILED,BOUNCED)") String statuses,
            @Parameter(description = "이메일 유형 (DEADLINE | RECOMMENDATION)") NotificationType emailType,
            @Parameter(description = "수신자 이메일 (부분 일치)") String recipient,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본: 0)") int page,
            @Parameter(description = "페이지 크기 (기본: 20, 최대: 200)") int size);

    @Operation(summary = "이메일 발송 시도 상세 조회",
            description = "ID로 단건 발송 시도의 상세 정보를 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시도 없음 (YF-004)")
    })
    ResponseEntity<ApiResponse<EmailAttemptDetailResponse>> detail(
            @Parameter(description = "발송 시도 ID") Long id);

    @Operation(summary = "이메일 발송 일별 통계",
            description = "기간 내 날짜별 상태(SENT/DELIVERED/BOUNCED/COMPLAINED/FAILED) 카운트")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<ApiResponse<List<EmailAttemptDailyStatsResponse>>> dailyStats(
            @Parameter(description = "시작일 (yyyy-MM-dd)") LocalDate from,
            @Parameter(description = "종료일 (yyyy-MM-dd)") LocalDate to);

    @Operation(summary = "이메일 발송 KPI 요약",
            description = "오늘 / 최근 7일 버킷과 발송 성공률을 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<ApiResponse<EmailAttemptKpiResponse>> kpi();

    @Operation(summary = "이메일 발송 시도 본문 미리보기",
            description = "저장된 input_payload 를 기반으로 이메일 HTML/TEXT 를 렌더링해 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시도 없음 (YF-004)")
    })
    ResponseEntity<ApiResponse<EmailAttemptPreviewResponse>> preview(
            @Parameter(description = "발송 시도 ID") Long id);

    @Operation(summary = "이메일 재발송 (FAILED 상태만)",
            description = "FAILED 상태인 발송 시도를 재발송하고 새 시도 ID 를 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "재발송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "FAILED 상태가 아님 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "시도 없음 (YF-004)")
    })
    ResponseEntity<ApiResponse<Map<String, Long>>> redispatch(
            @Parameter(description = "발송 시도 ID") Long id);
}
