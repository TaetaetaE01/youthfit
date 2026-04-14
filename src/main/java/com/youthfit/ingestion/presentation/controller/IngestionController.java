package com.youthfit.ingestion.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.service.IngestionService;
import com.youthfit.ingestion.presentation.dto.request.IngestPolicyRequest;
import com.youthfit.ingestion.presentation.dto.response.IngestPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "수집 (내부)", description = "n8n 연동 정책 수집 내부 API")
@RestController
@RequestMapping("/api/internal/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @Operation(summary = "정책 수집", description = "외부 수집 파이프라인에서 정책 데이터를 수신한다")
    @PostMapping("/policies")
    public ResponseEntity<ApiResponse<IngestPolicyResponse>> receivePolicy(
            @Valid @RequestBody IngestPolicyRequest request) {

        IngestPolicyResult result = ingestionService.receivePolicy(request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(IngestPolicyResponse.from(result)));
    }
}
