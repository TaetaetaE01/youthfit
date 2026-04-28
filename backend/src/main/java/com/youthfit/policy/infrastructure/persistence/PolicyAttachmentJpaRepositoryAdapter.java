package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyAttachmentJpaRepositoryAdapter implements PolicyAttachmentRepository {

    private final PolicyAttachmentJpaRepository jpa;

    @Override
    public Optional<PolicyAttachment> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public PolicyAttachment save(PolicyAttachment attachment) {
        return jpa.save(attachment);
    }

    @Override
    public List<PolicyAttachment> findPendingForDownload(int limit) {
        return jpa.findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus.PENDING, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findDownloadedForExtraction(int limit) {
        return jpa.findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus.DOWNLOADED, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findFailedRetryable(int limit, int retryLimit) {
        return jpa.findFailedRetryable(retryLimit, Limit.of(limit));
    }

    @Override
    public List<PolicyAttachment> findByPolicyIdAndStatusEquals(Long policyId, AttachmentStatus status) {
        return jpa.findByPolicy_IdAndExtractionStatus(policyId, status);
    }

    @Override
    public boolean isAllTerminalForPolicy(Long policyId) {
        return jpa.countNonTerminalByPolicyId(policyId) == 0L;
    }

    @Override
    public List<PolicyAttachment> findExtractedByPolicyId(Long policyId) {
        return findByPolicyIdAndStatusEquals(policyId, AttachmentStatus.EXTRACTED);
    }

    @Override
    public List<PolicyAttachment> findByPolicyId(Long policyId) {
        return jpa.findByPolicy_Id(policyId);
    }
}
