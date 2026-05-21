package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.presentation.dto.request.EnrichmentCallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Enrichment Callback (내부)", description = "n8n enrichment 작업 콜백 내부 API")
public interface InternalEnrichmentJobCallbackApi {

    @Operation(
            summary = "Enrichment 작업 결과 콜백",
            description = "n8n enrichment 파이프라인이 작업 완료 후 결과(RUNNING / SUCCESS / FAILED)를 전달한다. X-Internal-Api-Key 헤더가 필수다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "콜백 처리 성공"),
            @ApiResponse(responseCode = "400", description = "필수 필드(status) 누락 또는 유효하지 않은 값"),
            @ApiResponse(responseCode = "401", description = "Internal API Key 누락 / 불일치"),
            @ApiResponse(responseCode = "404", description = "해당 jobId 를 찾을 수 없음")
    })
    @SecurityRequirements
    ResponseEntity<Void> callback(
            @Parameter(description = "Enrichment Job ID", required = true) Long jobId,
            EnrichmentCallbackRequest body);
}
