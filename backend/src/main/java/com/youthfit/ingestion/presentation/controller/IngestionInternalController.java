package com.youthfit.ingestion.presentation.controller;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.ingestion.application.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 내부 운영용 ingestion 엔드포인트. InternalApiKeyFilter 보호.
 * 외부 사용자가 호출할 수 없고, 운영 스크립트/n8n 에서만 사용.
 */
@RestController
@RequestMapping("/api/internal/ingestion")
@RequiredArgsConstructor
public class IngestionInternalController implements IngestionInternalApi {

    private final AttachmentReindexService attachmentReindexService;
    private final IngestionService ingestionService;

    @PostMapping("/reindex/{policyId:\\d+}")
    @Override
    public ResponseEntity<Void> reindex(@PathVariable Long policyId) {
        attachmentReindexService.reindex(policyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/policies/external-hashes")
    @Override
    public ResponseEntity<Map<String, String>> getExternalHashes(
            @RequestParam("source") String source) {
        return ResponseEntity.ok(ingestionService.findExternalIdHashes(source));
    }
}
