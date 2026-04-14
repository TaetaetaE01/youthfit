package com.youthfit.ingestion.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.ingestion.presentation.dto.request.IngestPolicyRequest;
import com.youthfit.ingestion.presentation.dto.response.IngestPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "수집 (내부)", description = "n8n 연동 정책 수집 내부 API")
public interface IngestionApi {

    @Operation(summary = "정책 수집", description = "외부 수집 파이프라인에서 정책 데이터를 수신한다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "수신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값이 올바르지 않습니다 (YF-001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류가 발생했습니다 (YF-500)")
    })
    @SecurityRequirements
    ResponseEntity<ApiResponse<IngestPolicyResponse>> receivePolicy(IngestPolicyRequest request);
}
