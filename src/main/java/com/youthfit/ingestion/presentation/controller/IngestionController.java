package com.youthfit.ingestion.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.ingestion.application.dto.result.IngestPolicyResult;
import com.youthfit.ingestion.application.service.IngestionService;
import com.youthfit.ingestion.presentation.dto.request.IngestPolicyRequest;
import com.youthfit.ingestion.presentation.dto.response.IngestPolicyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ingestion")
@RequiredArgsConstructor
public class IngestionController implements IngestionApi {

    private final IngestionService ingestionService;

    @PostMapping("/policies")
    @Override
    public ResponseEntity<ApiResponse<IngestPolicyResponse>> receivePolicy(
            @Valid @RequestBody IngestPolicyRequest request) {

        IngestPolicyResult result = ingestionService.receivePolicy(request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(IngestPolicyResponse.from(result)));
    }
}
