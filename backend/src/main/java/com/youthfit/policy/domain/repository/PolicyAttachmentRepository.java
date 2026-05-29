package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;

import java.util.List;
import java.util.Map;
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

    /**
     * 정책 다건의 첨부 extraction_status 카운트 집계.
     * 첨부가 없는 정책은 결과 맵에서 누락됨 (호출자가 {@link AttachmentExtractionCounts#empty()} 로 처리).
     *
     * @return policyId -&gt; {@link AttachmentExtractionCounts}
     */
    Map<Long, AttachmentExtractionCounts> aggregateExtractionByPolicyIds(List<Long> policyIds);
}
