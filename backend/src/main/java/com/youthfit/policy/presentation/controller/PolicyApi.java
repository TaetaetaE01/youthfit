package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.SourceType;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarListResponse;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarPageResponse;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

@Tag(name = "정책", description = "정책 목록 조회, 상세 조회, 키워드 검색, 달력 조회 API")
public interface PolicyApi {

    @Operation(summary = "정책 목록 조회",
            description = "필터 조건에 따라 정책 목록을 페이징 조회한다. regions 는 행정코드 CSV (예: '11680,11440,11')."
                    + " 시·도(2자리)/시·군·구(5자리) 혼합 가능. 'NATIONWIDE' 단독은 전국 정책만 반환."
                    + " 그 외에는 '전국' 정책이 항상 OR 로 포함된다."
                    + " regionCode(단수) 는 deprecated — regions 가 있으면 무시된다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyPageResponse> findPolicies(
            @Parameter(description = "행정코드 CSV. 예: '11680,11440' 또는 'NATIONWIDE'") String regions,
            @Parameter(description = "[Deprecated] 단일 지역 코드. regions 가 있으면 무시.") String regionCode,
            Category category,
            @Parameter(description = "정책 상태 필터: OPEN(진행중) / UPCOMING(예정) / CLOSED(마감). 미지정 시 전체.")
            PolicyStatus status,
            @Parameter(description = "출처 (YOUTH_SEOUL_CRAWL, BOKJIRO_CENTRAL, YOUTH_CENTER)")
            SourceType source,
            int page,
            int size);

    @Operation(summary = "정책 상세 조회", description = "정책 ID로 상세 정보를 조회한다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없습니다 (YF-004)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyDetailResponse> getPolicyDetail(
            @Parameter(description = "정책 ID") Long policyId);

    @Operation(summary = "정책 키워드 검색",
            description = "키워드로 정책을 검색한다. status를 함께 전달하면 동일한 정렬 규칙이 적용된다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyPageResponse> searchPolicies(
            String keyword,
            @Parameter(description = "정책 상태 필터: OPEN / UPCOMING / CLOSED. 미지정 시 전체.")
            PolicyStatus status,
            int page,
            int size);

    @Operation(summary = "정책 달력 — 기간 overlap 조회",
            description = "from~to 와 신청 기간이 겹치는 정책을 가벼운 DTO 로 반환한다."
                    + " applyStart 또는 applyEnd 가 null 인 정책도 한쪽 경계로 포함되며,"
                    + " 둘 다 null 인 상시 정책은 /calendar/always-open 에서 따로 조회한다."
                    + " from > to 또는 92일 초과 범위는 400 (YF-001).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyCalendarListResponse> findCalendarPolicies(
            @Parameter(description = "조회 시작일 (YYYY-MM-DD)")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD). from <= to, to - from <= 92일")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "행정코드 CSV. 예: '11680,11440' 또는 'NATIONWIDE'") String regions,
            @Parameter(description = "[Deprecated] 단일 지역 코드. regions 가 있으면 무시.") String regionCode,
            Category category);

    @Operation(summary = "상시 모집 정책 조회",
            description = "applyStart=null AND applyEnd=null 인 정책을 페이징 조회한다."
                    + " referenceYear 가 현재 연도보다 이전인 만료 정책은 제외된다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<PolicyCalendarPageResponse> findAlwaysOpenPolicies(
            @Parameter(description = "행정코드 CSV. 예: '11680,11440' 또는 'NATIONWIDE'") String regions,
            @Parameter(description = "[Deprecated] 단일 지역 코드. regions 가 있으면 무시.") String regionCode,
            Category category,
            @Parameter(description = "0-base 페이지 번호") int page,
            @Parameter(description = "페이지 크기 (1~50)") int size);
}
