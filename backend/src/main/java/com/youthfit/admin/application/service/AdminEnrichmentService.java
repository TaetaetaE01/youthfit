package com.youthfit.admin.application.service;

import com.youthfit.admin.presentation.dto.request.ReferenceSiteRequest;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateSummaryResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentJobResponse;
import com.youthfit.admin.presentation.dto.response.PolicyEnrichmentDetailResponse;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.command.EnrichmentReviewFilterCommand;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.service.PolicyReferenceSiteMerger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어드민 enrichment 검토 콘솔 유스케이스 서비스.
 *
 * <p>Presentation 컨트롤러가 도메인 리포지토리 / 도메인 서비스에 직접 의존하지 않도록
 * 트랜잭션 경계와 DTO 변환을 응용 계층에서 캡슐화한다.
 */
@Service
@RequiredArgsConstructor
public class AdminEnrichmentService {

    private final AdminEnrichmentQueryService queryService;
    private final EnrichmentJobService jobService;
    private final PolicyRepository policyRepository;
    private final EnrichmentJobRepository jobRepository;
    private final PolicyAttachmentRepository attachmentRepository;
    private final PolicyReferenceSiteMerger referenceSiteMerger;

    @Transactional(readOnly = true)
    public Page<EnrichmentCandidateResponse> listCandidates(EnrichmentReviewFilterCommand filter, Pageable pageable) {
        Page<Policy> page = queryService.findReviewCandidates(filter, pageable);
        return page.map(policy -> {
            List<EnrichmentJob> recent = jobRepository.findRecentByPolicyId(policy.getId(), 1);
            EnrichmentJob latest = recent.isEmpty() ? null : recent.get(0);
            return EnrichmentCandidateResponse.of(policy, latest, queryService.needsReview(policy));
        });
    }

    @Transactional(readOnly = true)
    public EnrichmentCandidateSummaryResponse findSummary() {
        return EnrichmentCandidateSummaryResponse.of(queryService.findReviewSummary());
    }

    @Transactional(readOnly = true)
    public PolicyEnrichmentDetailResponse findDetail(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "Policy not found: " + policyId));
        List<EnrichmentJob> jobs = jobRepository.findRecentByPolicyId(policyId, 5);
        List<PolicyAttachment> attachments = attachmentRepository.findByPolicyId(policyId);
        return PolicyEnrichmentDetailResponse.of(policy, jobs, queryService.needsReview(policy), attachments);
    }

    @Transactional
    public void updateReferenceSites(Long policyId, List<ReferenceSiteRequest> requests) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "Policy not found: " + policyId));
        List<PolicyReferenceSite> adminInputs = requests == null ? List.of() : requests.stream()
                .map(r -> new PolicyReferenceSite(r.name(), r.url(), PolicyReferenceSiteSource.ADMIN))
                .toList();
        List<PolicyReferenceSite> merged = referenceSiteMerger.merge(policy.getReferenceSites(), adminInputs);
        policy.replaceReferenceSites(merged);
        policyRepository.save(policy);
    }

    /**
     * 강제 enrichment 잡 생성.
     * urlsOverride 가 null/empty 이면 Policy 의 referenceSites 가 그대로 사용된다.
     */
    public EnrichmentJobResponse createJob(Long policyId, String requestedBy, List<ReferenceSiteRequest> urlsOverride) {
        List<PolicyReferenceSite> urls = (urlsOverride == null) ? null : urlsOverride.stream()
                .map(u -> new PolicyReferenceSite(u.name(), u.url(), PolicyReferenceSiteSource.ADMIN))
                .toList();
        EnrichmentJob job = jobService.create(policyId, requestedBy, urls);
        return EnrichmentJobResponse.of(job);
    }

    @Transactional(readOnly = true)
    public EnrichmentJobResponse findJob(Long jobId) {
        EnrichmentJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new YouthFitException(ErrorCode.NOT_FOUND, "Job not found: " + jobId));
        return EnrichmentJobResponse.of(job);
    }
}
