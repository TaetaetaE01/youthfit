package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.presentation.dto.request.ApplyPolicyEnrichmentRequest;
import com.youthfit.policy.application.service.PolicyIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalPolicyEnrichmentController implements InternalPolicyEnrichmentApi {

    private final PolicyIngestionService policyIngestionService;

    @PostMapping("/policy-enrichment")
    @Override
    public ResponseEntity<Void> applyEnrichment(@Valid @RequestBody ApplyPolicyEnrichmentRequest request) {
        policyIngestionService.applyEnrichment(request.policyId(), request.enrichmentResult());
        return ResponseEntity.noContent().build();
    }
}
