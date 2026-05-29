package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.PolicyAttachment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 정책 다건의 첨부 extraction_status 카운트 집계.
     * row = [policyId, total, downloaded(>=DOWNLOADED bucket), extracted].
     * 첨부가 없는 정책은 결과에서 제외됨 (group by 결과).
     */
    @Query("""
            SELECT a.policy.id,
                   COUNT(a),
                   SUM(CASE WHEN a.extractionStatus IN (
                       com.youthfit.policy.domain.model.AttachmentStatus.DOWNLOADED,
                       com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTING,
                       com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED,
                       com.youthfit.policy.domain.model.AttachmentStatus.FAILED,
                       com.youthfit.policy.domain.model.AttachmentStatus.SKIPPED
                   ) THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.extractionStatus = com.youthfit.policy.domain.model.AttachmentStatus.EXTRACTED
                       THEN 1 ELSE 0 END)
            FROM PolicyAttachment a
            WHERE a.policy.id IN :policyIds
            GROUP BY a.policy.id
            """)
    List<Object[]> aggregateExtractionByPolicyIdsRaw(@Param("policyIds") List<Long> policyIds);
}
