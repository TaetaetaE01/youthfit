package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.presentation.dto.request.EnrichmentCallbackRequest;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/enrichment")
@RequiredArgsConstructor
public class InternalEnrichmentJobCallbackController implements InternalEnrichmentJobCallbackApi {

    private final EnrichmentJobService service;

    @PostMapping("/jobs/{jobId}/callback")
    @Override
    public ResponseEntity<Void> callback(@PathVariable Long jobId,
                                         @Valid @RequestBody EnrichmentCallbackRequest body) {
        switch (body.status()) {
            case RUNNING -> service.markRunning(jobId);
            case SUCCESS -> service.complete(jobId, EnrichmentJobStatus.SUCCESS, null);
            case FAILED  -> service.complete(jobId, EnrichmentJobStatus.FAILED, body.error());
            case PENDING -> { /* no-op */ }
        }
        return ResponseEntity.noContent().build();
    }
}
