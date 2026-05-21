package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.command.EnrichmentReviewFilterCommand;
import com.youthfit.policy.application.dto.result.EnrichmentReviewSummaryResult;
import com.youthfit.policy.application.port.AdminEnrichmentSummaryReader;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.service.EnrichmentReviewPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 enrichment 검토(read-side) 유스케이스.
 * 검토 대상 정책 후보 페이지 조회와 현황 요약을 제공한다.
 */
@Service
@Transactional(readOnly = true)
public class AdminEnrichmentQueryService {

    private final PolicyRepository policyRepository;
    private final AdminEnrichmentSummaryReader summaryReader;
    private final EnrichmentReviewPolicy reviewPolicy;

    public AdminEnrichmentQueryService(PolicyRepository policyRepository,
                                       AdminEnrichmentSummaryReader summaryReader,
                                       EnrichmentReviewPolicy reviewPolicy) {
        this.policyRepository = policyRepository;
        this.summaryReader = summaryReader;
        this.reviewPolicy = reviewPolicy;
    }

    public Page<Policy> findReviewCandidates(EnrichmentReviewFilterCommand filter, Pageable pageable) {
        boolean needsReviewOnly = filter != null && Boolean.TRUE.equals(filter.needsReviewOnly());
        String keyword = filter == null ? null : filter.keyword();
        return policyRepository.searchForEnrichmentReview(needsReviewOnly, keyword, pageable);
    }

    public EnrichmentReviewSummaryResult findReviewSummary() {
        return summaryReader.readSummary();
    }

    public boolean needsReview(Policy policy) {
        return reviewPolicy.needsReview(policy);
    }
}
