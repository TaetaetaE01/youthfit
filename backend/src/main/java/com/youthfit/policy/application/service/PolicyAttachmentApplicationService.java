package com.youthfit.policy.application.service;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyAttachmentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAttachmentApplicationService.class);

    private final PolicyAttachmentRepository repository;

    @Transactional
    public void markDownloading(Long id) {
        load(id).markDownloading();
    }

    @Transactional
    public void markDownloaded(Long id, String storageKey, String fileHash) {
        load(id).markDownloaded(storageKey, fileHash);
    }

    @Transactional
    public void markExtracting(Long id) {
        load(id).markExtracting();
    }

    @Transactional
    public void markExtracted(Long id, String text) {
        load(id).markExtracted(text);
    }

    @Transactional
    public void markSkipped(Long id, SkipReason reason) {
        load(id).markSkipped(reason);
    }

    @Transactional
    public void markFailed(Long id, String error) {
        load(id).markFailed(error);
    }

    @Transactional
    public int resetFailedToPending(int limit, int retryLimit) {
        List<PolicyAttachment> failed = repository.findFailedRetryable(limit, retryLimit);
        failed.forEach(PolicyAttachment::resetFailedToPending);
        if (!failed.isEmpty()) {
            log.info("Reset {} FAILED attachments to PENDING", failed.size());
        }
        return failed.size();
    }

    @Transactional
    public void markPendingReextraction(Long policyId) {
        List<PolicyAttachment> all = repository.findByPolicyId(policyId);
        all.forEach(PolicyAttachment::markPendingReextraction);
        log.info("Marked {} attachments PENDING for reextraction (policyId={})", all.size(), policyId);
    }

    private PolicyAttachment load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND));
    }
}
