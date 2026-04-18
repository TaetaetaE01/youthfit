package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.request.UpdateEligibilityProfileRequest;
import com.youthfit.user.presentation.dto.response.EligibilityProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "적합도 프로필", description = "사용자 적합도 판정 프로필 조회 및 수정 API")
public interface EligibilityProfileApi {

    @Operation(summary = "내 적합도 프로필 조회",
            description = "로그인한 사용자의 적합도 판정 프로필을 조회한다. 프로필이 없으면 빈 프로필을 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<EligibilityProfileResponse>> findMyEligibilityProfile(Authentication authentication);

    @Operation(summary = "내 적합도 프로필 부분 수정",
            description = "로그인한 사용자의 적합도 프로필을 부분 수정한다. 요청에 포함된 필드만 변경되며, 생략된 필드는 기존 값을 유지한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<EligibilityProfileResponse>> updateMyEligibilityProfile(
            Authentication authentication,
            UpdateEligibilityProfileRequest request);
}
