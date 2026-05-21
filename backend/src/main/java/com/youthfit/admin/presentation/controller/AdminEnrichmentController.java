package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminEnrichmentService;
import com.youthfit.admin.presentation.dto.request.CreateJobRequest;
import com.youthfit.admin.presentation.dto.request.ReferenceSiteRequest;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentCandidateSummaryResponse;
import com.youthfit.admin.presentation.dto.response.EnrichmentJobResponse;
import com.youthfit.admin.presentation.dto.response.JobAcceptedResponse;
import com.youthfit.admin.presentation.dto.response.PolicyEnrichmentDetailResponse;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.application.dto.command.EnrichmentReviewFilterCommand;
import com.youthfit.policy.domain.model.DetailLevel;
import com.youthfit.policy.domain.model.EnrichmentStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * <p>비즈니스 로직 / 트랜잭션 / 도메인 리포지토리 접근은 {@link AdminEnrichmentService}로 위임한다.
 * Swagger 어노테이션은 {@link AdminEnrichmentApi} 인터페이스에 위치한다.
 */
@RestController
@RequestMapping("/api/v1/admin/enrichment")
@RequiredArgsConstructor
public class AdminEnrichmentController implements AdminEnrichmentApi {

    private final AdminEnrichmentService adminEnrichmentService;

    @Override
    @GetMapping("/candidates")
    public Page<EnrichmentCandidateResponse> getCandidates(
            @RequestParam(defaultValue = "true") Boolean needsReview,
            @RequestParam(required = false) Set<EnrichmentStatus> statuses,
            @RequestParam(required = false) Set<DetailLevel> detailLevels,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        EnrichmentReviewFilterCommand filter =
                new EnrichmentReviewFilterCommand(needsReview, statuses, detailLevels, q);
        return adminEnrichmentService.listCandidates(filter, pageable);
    }

    @Override
    @GetMapping("/candidates/summary")
    public EnrichmentCandidateSummaryResponse getCandidateSummary() {
        return adminEnrichmentService.findSummary();
    }

    @Override
    @GetMapping("/policies/{policyId}")
    public PolicyEnrichmentDetailResponse getPolicyDetail(@PathVariable Long policyId) {
        return adminEnrichmentService.findDetail(policyId);
    }

    @Override
    @PutMapping("/policies/{policyId}/reference-sites")
    public ResponseEntity<Void> updateReferenceSites(@PathVariable Long policyId,
                                                     @RequestBody @Valid List<ReferenceSiteRequest> body) {
        adminEnrichmentService.updateReferenceSites(policyId, body);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/policies/{policyId}/jobs")
    public ResponseEntity<JobAcceptedResponse> createJob(@PathVariable Long policyId,
                                                         @RequestBody(required = false) CreateJobRequest body,
                                                         Authentication authentication) {
        // SecurityConfig 에서 ROLE_ADMIN 을 강제하므로 실제로는 도달 불가능하지만,
        // 감사 로그에 "unknown" 같은 리터럴이 기록되지 않도록 명시적으로 401 을 던진다.
        if (authentication == null || authentication.getName() == null) {
            throw new YouthFitException(ErrorCode.UNAUTHORIZED);
        }
        String actor = authentication.getName();
        List<ReferenceSiteRequest> urlsOverride = (body == null) ? null : body.urls();
        EnrichmentJobResponse job = adminEnrichmentService.createJob(policyId, actor, urlsOverride);
        return ResponseEntity.accepted()
                .body(new JobAcceptedResponse(job.id(), job.status()));
    }

    @Override
    @GetMapping("/jobs/{jobId}")
    public EnrichmentJobResponse getJob(@PathVariable Long jobId) {
        return adminEnrichmentService.findJob(jobId);
    }
}
