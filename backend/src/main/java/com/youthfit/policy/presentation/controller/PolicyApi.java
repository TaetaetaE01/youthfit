package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "정책", description = "정책 목록 조회, 상세 조회, 키워드 검색 API")
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
}
