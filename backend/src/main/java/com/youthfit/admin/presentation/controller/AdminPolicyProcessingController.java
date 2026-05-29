package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.dto.PolicyProcessingFilter;
import com.youthfit.admin.application.dto.PolicyProcessingListCommand;
import com.youthfit.admin.application.dto.PolicyProcessingSort;
import com.youthfit.admin.application.service.AdminPolicyProcessingService;
import com.youthfit.admin.presentation.api.AdminPolicyProcessingApi;
import com.youthfit.admin.presentation.dto.request.ReprocessPolicyRequest;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingDetailResponse;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingListResponse;
import com.youthfit.admin.presentation.dto.response.PolicyProcessingStatsResponse;
import com.youthfit.admin.presentation.dto.response.ReprocessResponse;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import com.youthfit.policy.domain.model.ProcessingStep;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 — 정책 처리 현황 대시보드 컨트롤러.
 *
 * <p>Phase 1 에서 조회 endpoint 3종 + Phase 2 (Task 10~14) 에서 재실행 endpoint 5종을 노출한다.</p>
 *
 * <p>URL prefix 는 SecurityConfig 의 {@code /api/v1/admin/**} ADMIN 가드와
 * 다른 admin 컨트롤러 컨벤션을 따른다. plan 본문은 {@code /admin/policies/processing} 만 명시했지만
 * 어드민 가드가 적용되도록 동일한 prefix 를 사용한다.</p>
 *
 * <p>Swagger 어노테이션은 {@link AdminPolicyProcessingApi} 인터페이스에 위치한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/policies/processing")
@RequiredArgsConstructor
public class AdminPolicyProcessingController implements AdminPolicyProcessingApi {

    private final AdminPolicyProcessingService service;

    @Override
    @GetMapping
    public ResponseEntity<PolicyProcessingListResponse> getPolicies(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "UPDATED_DESC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PolicyProcessingListCommand command = new PolicyProcessingListCommand(
                q,
                region,
                PolicyProcessingFilter.valueOf(filter),
                PolicyProcessingSort.valueOf(sort),
                page,
                size
        );
        return ResponseEntity.ok(PolicyProcessingListResponse.from(service.findProcessingPolicies(command)));
    }

    @Override
    @GetMapping("/stats")
    public ResponseEntity<PolicyProcessingStatsResponse> getStats() {
        return ResponseEntity.ok(PolicyProcessingStatsResponse.from(service.findProcessingStats()));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<PolicyProcessingDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(PolicyProcessingDetailResponse.from(service.findProcessingDetail(id)));
    }

    @Override
    @PostMapping("/{id}/steps/{step}/retry")
    public ResponseEntity<ReprocessResponse> retryStep(@PathVariable Long id, @PathVariable String step) {
        ProcessingStep processingStep;
        try {
            processingStep = ProcessingStep.valueOf(step.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT,
                    "알 수 없는 ProcessingStep: " + step);
        }
        return ResponseEntity.ok(ReprocessResponse.from(service.retryStep(id, processingStep)));
    }

    @Override
    @PostMapping("/{id}/attachments/{attachmentId}/reindex")
    public ResponseEntity<ReprocessResponse> reindexAttachment(@PathVariable Long id,
                                                                @PathVariable Long attachmentId) {
        return ResponseEntity.ok(ReprocessResponse.from(service.reindexAttachment(id, attachmentId)));
    }

    @Override
    @PostMapping("/{id}/attachments/reindex")
    public ResponseEntity<ReprocessResponse> reindexAllAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(ReprocessResponse.from(service.reindexAllAttachments(id)));
    }

    @Override
    public ResponseEntity<ReprocessResponse> reindexRag(Long id) {
        throw new UnsupportedOperationException("Phase 2");
    }

    @Override
    public ResponseEntity<ReprocessResponse> reprocess(Long id, ReprocessPolicyRequest request) {
        throw new UnsupportedOperationException("Phase 2");
    }
}
