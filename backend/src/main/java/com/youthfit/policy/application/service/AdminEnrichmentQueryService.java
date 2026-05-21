package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.EnrichmentReviewFilter;
import com.youthfit.policy.application.dto.EnrichmentReviewSummary;
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
    private final EnrichmentReviewPolicy reviewPolicy;

    public AdminEnrichmentQueryService(PolicyRepository policyRepository,
                                       EnrichmentReviewPolicy reviewPolicy) {
        this.policyRepository = policyRepository;
        this.reviewPolicy = reviewPolicy;
    }

    public Page<Policy> candidates(EnrichmentReviewFilter filter, Pageable pageable) {
        return policyRepository.searchForEnrichmentReview(filter, pageable);
    }

    public EnrichmentReviewSummary summary() {
        return policyRepository.summarizeEnrichmentReview();
    }

    public boolean needsReview(Policy policy) {
        return reviewPolicy.needsReview(policy);
    }
}
