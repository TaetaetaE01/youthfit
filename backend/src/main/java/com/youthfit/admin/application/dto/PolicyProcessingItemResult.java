package com.youthfit.admin.application.dto;

import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;

import java.time.LocalDateTime;
import java.util.Map;

public record PolicyProcessingItemResult(
    Long policyId,
    String title,
    String region,
    PolicyProcessingCompleteness completeness,
    Map<ProcessingStep, ProcessingStatus> stepStatuses,
    AttachmentSummaryResult attachments,
    ReferenceSummaryResult references,
    LocalDateTime updatedAt
) {}
