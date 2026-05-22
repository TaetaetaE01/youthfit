package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Dashboard", description = "지통실 대시보드 (ROLE_ADMIN 필수)")
public interface AdminDashboardApi {

    @Operation(summary = "어드민 대시보드 오버뷰",
            description = "발화된 액션 아이템과 6개 영역 카드 상태를 함께 반환한다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview();
}
