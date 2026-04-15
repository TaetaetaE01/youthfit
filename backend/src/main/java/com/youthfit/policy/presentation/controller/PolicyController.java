package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController implements PolicyApi {

    private final PolicyQueryService policyQueryService;

    @GetMapping
    @Override
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

    @GetMapping("/{policyId}")
    @Override
    public ResponseEntity<PolicyDetailResponse> getPolicyDetail(@PathVariable Long policyId) {
        PolicyDetailResult result = policyQueryService.findPolicyById(policyId);
        return ResponseEntity.ok(PolicyDetailResponse.from(result));
    }

    @GetMapping("/search")
    @Override
    public ResponseEntity<PolicyPageResponse> searchPolicies(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.searchPoliciesByKeyword(keyword, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }
}
