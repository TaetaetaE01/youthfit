package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.CandidateSummaryView;
import com.youthfit.admin.presentation.dto.CandidateView;
import com.youthfit.admin.presentation.dto.CreateJobRequest;
import com.youthfit.admin.presentation.dto.EnrichmentJobView;
import com.youthfit.admin.presentation.dto.JobAcceptedResponse;
import com.youthfit.admin.presentation.dto.PolicyEnrichmentDetailView;
import com.youthfit.admin.presentation.dto.ReferenceSiteRequest;
import com.youthfit.policy.application.dto.command.EnrichmentReviewFilterCommand;
import com.youthfit.policy.application.service.AdminEnrichmentQueryService;
import com.youthfit.policy.application.service.EnrichmentJobService;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.domain.service.PolicyReferenceSiteMerger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 관리자 enrichment 검토 콘솔용 컨트롤러.
 *
 * <p>다음 유스케이스를 제공한다.
 * <ul>
 *   <li>검토 대상 후보 목록 / 현황 요약</li>
 *   <li>정책별 enrichment 상세 + 최근 잡 이력</li>
 *   <li>관리자 입력 reference site URL 갱신</li>
 *   <li>강제 enrichment 잡 생성</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/enrichment")
@RequiredArgsConstructor
public class AdminEnrichmentController {

    private final AdminEnrichmentQueryService queryService;
    private final EnrichmentJobService jobService;
    private final PolicyRepository policyRepo;
    private final EnrichmentJobRepository jobRepo;
    private final PolicyReferenceSiteMerger merger;

    @GetMapping("/candidates")
    public Page<CandidateView> candidates(
            @RequestParam(defaultValue = "true") Boolean needsReview,
            @RequestParam(required = false) Set<EnrichmentStatus> statuses,
            @RequestParam(required = false) Set<DetailLevel> detailLevels,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        EnrichmentReviewFilterCommand filter =
                new EnrichmentReviewFilterCommand(needsReview, statuses, detailLevels, q);
        Page<Policy> page = queryService.findReviewCandidates(filter, pageable);
        return page.map(p -> {
            List<EnrichmentJob> recent = jobRepo.findRecentByPolicyId(p.getId(), 1);
            EnrichmentJob latest = recent.isEmpty() ? null : recent.get(0);
            return CandidateView.of(p, latest, queryService.needsReview(p));
        });
    }

    @GetMapping("/candidates/summary")
    public CandidateSummaryView summary() {
        return CandidateSummaryView.of(queryService.findReviewSummary());
    }

    @GetMapping("/policies/{policyId}")
    public PolicyEnrichmentDetailView detail(@PathVariable Long policyId) {
        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        List<EnrichmentJob> jobs = jobRepo.findRecentByPolicyId(policyId, 5);
        return PolicyEnrichmentDetailView.of(policy, jobs, queryService.needsReview(policy));
    }

    @PutMapping("/policies/{policyId}/reference-sites")
    @Transactional
    public ResponseEntity<Void> updateReferenceSites(@PathVariable Long policyId,
                                                     @RequestBody @Valid List<ReferenceSiteRequest> body) {
        Policy policy = policyRepo.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        List<PolicyReferenceSite> adminInputs = body == null ? List.of() : body.stream()
                .map(r -> new PolicyReferenceSite(r.name(), r.url(), PolicyReferenceSiteSource.ADMIN))
                .toList();
        List<PolicyReferenceSite> merged = merger.merge(policy.getReferenceSites(), adminInputs);
        policy.replaceReferenceSites(merged);
        policyRepo.save(policy);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/policies/{policyId}/jobs")
    public ResponseEntity<JobAcceptedResponse> createJob(@PathVariable Long policyId,
                                                         @RequestBody(required = false) CreateJobRequest body,
                                                         Authentication authentication) {
        List<PolicyReferenceSite> urls = (body == null || body.urls() == null) ? null
                : body.urls().stream()
                    .map(u -> new PolicyReferenceSite(u.name(), u.url(), PolicyReferenceSiteSource.ADMIN))
                    .toList();
        String actor = authentication == null ? "unknown" : authentication.getName();
        EnrichmentJob job = jobService.create(policyId, actor, urls);
        return ResponseEntity.accepted()
                .body(new JobAcceptedResponse(job.getId(), job.getStatus()));
    }

    @GetMapping("/jobs/{jobId}")
    public EnrichmentJobView job(@PathVariable Long jobId) {
        EnrichmentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return EnrichmentJobView.of(job);
    }
}
