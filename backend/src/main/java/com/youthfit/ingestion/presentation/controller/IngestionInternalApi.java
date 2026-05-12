package com.youthfit.ingestion.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "Ingestion Internal", description = "운영용 내부 ingestion API")
public interface IngestionInternalApi {

    @Operation(summary = "정책 reindex 트리거", description = "정책 본문 + 첨부를 합쳐 재청킹·재인덱싱한다. 운영 스크립트 / n8n 에서 호출하며 X-Internal-Api-Key 헤더가 필수다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "재인덱싱 트리거 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Internal API Key 누락 / 불일치")
    })
    @SecurityRequirements
    ResponseEntity<Void> reindex(
            @Parameter(description = "재인덱싱 대상 정책 id", required = true) Long policyId);

    @Operation(
            summary = "특정 source 의 external_id → source_hash 맵",
            description = "n8n 워크플로우가 변동 식별을 위해 호출. X-Internal-Api-Key 필수."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "external_id → hash 맵"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "400", description = "필수 파라미터 누락")
    })
    ResponseEntity<Map<String, String>> getExternalHashes(
            @Parameter(description = "source type (예: YOUTH_CENTER)", required = true)
            String source);
}
