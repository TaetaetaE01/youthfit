package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.presentation.dto.request.ApplyPolicyEnrichmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Ingestion Internal", description = "운영용 내부 ingestion API")
public interface InternalPolicyEnrichmentApi {

    @Operation(
            summary = "정책 enrichment 결과 적용",
            description = "force-enrich n8n 워크플로우가 외부 fetch + LLM 처리 결과를 backend 에 반영한다. " +
                    "policy.enrichment (jsonb) 컬럼을 덮어쓴다. X-Internal-Api-Key 헤더 필수."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "enrichment 적용 성공"),
            @ApiResponse(responseCode = "400", description = "요청 바디 검증 실패 (YF-001)"),
            @ApiResponse(responseCode = "401", description = "Internal API Key 누락/불일치 (YF-002)"),
            @ApiResponse(responseCode = "404", description = "policyId 에 해당하는 정책 없음 (YF-004)")
    })
    @SecurityRequirements
    ResponseEntity<Void> applyEnrichment(
            @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ApplyPolicyEnrichmentRequest.class)))
            ApplyPolicyEnrichmentRequest request);
}
