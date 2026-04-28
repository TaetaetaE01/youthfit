package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PolicyAttachmentJpaRepository extends JpaRepository<PolicyAttachment, Long> {

    List<PolicyAttachment> findByExtractionStatusOrderByUpdatedAtAsc(AttachmentStatus status, Limit limit);

    @Query("SELECT a FROM PolicyAttachment a "
            + "WHERE a.extractionStatus = com.youthfit.policy.domain.model.AttachmentStatus.FAILED "
            + "AND a.extractionRetryCount < :retryLimit "
            + "ORDER BY a.updatedAt ASC")
    List<PolicyAttachment> findFailedRetryable(int retryLimit, Limit limit);

    List<PolicyAttachment> findByPolicy_IdAndExtractionStatus(Long policyId, AttachmentStatus status);

    List<PolicyAttachment> findByPolicy_Id(Long policyId);

    @Query("SELECT COUNT(a) FROM PolicyAttachment a "
            + "WHERE a.policy.id = :policyId "
            + "AND a.extractionStatus NOT IN ("
            + "  com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED, "
            + "  com.youthfit.policy.domain.model.AttachmentStatus.SKIPPED)")
    long countNonTerminalByPolicyId(Long policyId);
}
