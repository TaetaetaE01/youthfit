package com.youthfit.ingestion.application.service;

import com.youthfit.ingestion.application.port.AttachmentStorage;
import com.youthfit.ingestion.application.port.ExtractionResult;
import com.youthfit.policy.application.service.PolicyAttachmentApplicationService;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentExtractionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentExtractionScheduler.class);

    private final PolicyAttachmentRepository repository;
    private final PolicyAttachmentApplicationService stateService;
    private final AttachmentStorage storage;
    private final ExtractionDispatcher dispatcher;
    private final AttachmentDownloadService downloadService;
    private final AttachmentReindexService reindexService;

    @Setter
    @Value("${attachment.scheduler.batch-size:20}")
    private int batchSize;

    @Setter
    @Value("${attachment.extraction.retry-limit:3}")
    private int retryLimit;

    @Setter
    @Value("${attachment.extraction.min-text-chars:100}")
    private int minTextChars;

    @Scheduled(fixedDelayString = "${attachment.scheduler.fixed-delay-ms:60000}")
    public void runCycle() {
        // 4-1. FAILED 중 retryCount<retryLimit → PENDING
        stateService.resetFailedToPending(batchSize, retryLimit);

        // 4-2. PENDING 다운로드 (fallback / 백필)
        for (PolicyAttachment p : repository.findPendingForDownload(batchSize)) {
            downloadService.downloadOne(p.getId());
        }

        // 4-3. DOWNLOADED → 추출
        Set<Long> reindexCandidates = new HashSet<>();
        for (PolicyAttachment p : repository.findDownloadedForExtraction(batchSize)) {
            extractOne(p, reindexCandidates);
        }

        // 4-4. 정책별 완료 체크 → 재인덱스
        for (Long policyId : reindexCandidates) {
            if (repository.isAllTerminalForPolicy(policyId)) {
                try {
                    reindexService.reindex(policyId);
                } catch (Exception e) {
                    log.warn("reindex failed: policyId={}", policyId, e);
                }
            }
        }
    }

    private void extractOne(PolicyAttachment p, Set<Long> reindexCandidates) {
        Long id = p.getId();
        try {
            stateService.markExtracting(id);
        } catch (IllegalStateException e) {
            log.debug("extracting already in flight: {}", id);
            return;
        }

        try (InputStream in = storage.get(p.getStorageKey())) {
            ExtractionResult result = dispatcher.dispatch(in, 0, p.getMediaType());
            switch (result) {
                case ExtractionResult.Success s -> {
                    String text = s.text();
                    if (text == null || text.length() < minTextChars) {
                        stateService.markSkipped(id, SkipReason.SCANNED_PDF);
                    } else {
                        stateService.markExtracted(id, text);
                    }
                }
                case ExtractionResult.Skipped sk -> stateService.markSkipped(id, sk.reason());
                case ExtractionResult.Failed f -> stateService.markFailed(id, f.error());
            }
        } catch (Exception e) {
            stateService.markFailed(id, e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (p.getPolicy() != null) {
            reindexCandidates.add(p.getPolicy().getId());
        }
    }
}
