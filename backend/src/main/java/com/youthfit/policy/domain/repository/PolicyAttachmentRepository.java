package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;

import java.util.List;
import java.util.Optional;

public interface PolicyAttachmentRepository {
    Optional<PolicyAttachment> findById(Long id);
    PolicyAttachment save(PolicyAttachment attachment);

    List<PolicyAttachment> findPendingForDownload(int limit);
    List<PolicyAttachment> findDownloadedForExtraction(int limit);
    List<PolicyAttachment> findFailedRetryable(int limit, int retryLimit);
    List<PolicyAttachment> findByPolicyIdAndStatusEquals(Long policyId, AttachmentStatus status);

    /**
     * 정책에 PENDING/DOWNLOADING/DOWNLOADED/EXTRACTING/FAILED 가 하나도 없는지.
     * true = 모두 EXTRACTED 또는 SKIPPED.
     */
    boolean isAllTerminalForPolicy(Long policyId);

    List<PolicyAttachment> findExtractedByPolicyId(Long policyId);
    List<PolicyAttachment> findByPolicyId(Long policyId);
}
