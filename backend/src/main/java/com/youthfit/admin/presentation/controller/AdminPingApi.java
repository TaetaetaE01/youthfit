package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.response.AdminPingResponse;
import com.youthfit.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin", description = "어드민 전용 API (ROLE_ADMIN 필수)")
public interface AdminPingApi {

    @Operation(summary = "어드민 ping",
            description = "ROLE_ADMIN 권한 확인용 스모크 엔드포인트")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "pong"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 (YF-002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족 (YF-003)")
    })
    ResponseEntity<ApiResponse<AdminPingResponse>> ping();
}
