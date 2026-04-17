package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.presentation.dto.response.PolicyNotificationSubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "정책 알림 구독", description = "정책별 마감일 알림 구독/해제 API")
public interface PolicyNotificationSubscriptionApi {

    @Operation(summary = "정책 알림 구독 상태 조회", description = "로그인한 사용자가 해당 정책의 마감일 알림을 구독 중인지 조회한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<PolicyNotificationSubscriptionResponse>> findSubscription(
            Authentication authentication,
            @Parameter(description = "정책 ID") Long policyId);

    @Operation(summary = "정책 알림 구독", description = "해당 정책의 마감일 알림 구독을 등록한다. 이미 구독 중이면 현재 상태를 반환한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구독 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정책을 찾을 수 없습니다 (YF-004)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<PolicyNotificationSubscriptionResponse>> subscribe(
            Authentication authentication,
            @Parameter(description = "정책 ID") Long policyId);

    @Operation(summary = "정책 알림 구독 해제", description = "해당 정책의 마감일 알림 구독을 해제한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구독 해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    ResponseEntity<ApiResponse<Void>> unsubscribe(
            Authentication authentication,
            @Parameter(description = "정책 ID") Long policyId);
}
