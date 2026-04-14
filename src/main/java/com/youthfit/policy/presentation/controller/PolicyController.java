package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "정책", description = "정책 목록 조회, 상세 조회, 키워드 검색 API")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyQueryService policyQueryService;

    @Operation(summary = "정책 목록 조회", description = "필터 조건에 따라 정책 목록을 페이징 조회한다")
    @GetMapping
    public ResponseEntity<PolicyPageResponse> findPolicies(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                regionCode, category, status, sortBy, ascending, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }

    @Operation(summary = "정책 상세 조회", description = "정책 ID로 상세 정보를 조회한다")
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyDetailResponse> getPolicyDetail(
            @Parameter(description = "정책 ID") @PathVariable Long policyId) {
        PolicyDetailResult result = policyQueryService.findPolicyById(policyId);
        return ResponseEntity.ok(PolicyDetailResponse.from(result));
    }

    @Operation(summary = "정책 키워드 검색", description = "키워드로 정책을 검색한다")
    @GetMapping("/search")
    public ResponseEntity<PolicyPageResponse> searchPolicies(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.searchPoliciesByKeyword(keyword, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }
}
