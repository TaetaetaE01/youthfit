package com.youthfit.policy.presentation.controller;

import com.youthfit.policy.application.dto.result.PolicyCalendarPageResult;
import com.youthfit.policy.application.dto.result.PolicyCalendarResult;
import com.youthfit.policy.application.dto.result.PolicyDetailResult;
import com.youthfit.policy.application.dto.result.PolicyPageResult;
import com.youthfit.policy.application.service.PolicyQueryService;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarPageResponse;
import com.youthfit.policy.presentation.dto.response.PolicyCalendarResponse;
import com.youthfit.policy.presentation.dto.response.PolicyDetailResponse;
import com.youthfit.policy.presentation.dto.response.PolicyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController implements PolicyApi {

    private final PolicyQueryService policyQueryService;

    @GetMapping
    @Override
    public ResponseEntity<PolicyPageResponse> findPolicies(
            @RequestParam(required = false) String regions,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        RegionFilter filter = resolveRegionFilter(regions, regionCode);
        PolicyPageResult result = policyQueryService.findPoliciesByFilters(
                filter, category, status, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }

    private static RegionFilter resolveRegionFilter(String regions, String legacyRegionCode) {
        if (regions != null && !regions.isBlank()) {
            return RegionFilter.ofCsv(regions);
        }
        if (legacyRegionCode != null && !legacyRegionCode.isBlank()) {
            return RegionFilter.of(java.util.List.of(legacyRegionCode));
        }
        return RegionFilter.of(null);
    }

    @GetMapping("/{policyId}")
    @Override
    public ResponseEntity<PolicyDetailResponse> getPolicyDetail(@PathVariable Long policyId) {
        PolicyDetailResult result = policyQueryService.findPolicyById(policyId);
        return ResponseEntity.ok(PolicyDetailResponse.from(result));
    }

    @GetMapping("/calendar")
    public ResponseEntity<CalendarResponseWrapper> findCalendarPolicies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String regions,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Category category) {

        RegionFilter filter = resolveRegionFilter(regions, regionCode);
        List<PolicyCalendarResult> results =
                policyQueryService.findByDateRange(from, to, filter, category);
        List<PolicyCalendarResponse> items = results.stream()
                .map(PolicyCalendarResponse::from)
                .toList();
        return ResponseEntity.ok(new CalendarResponseWrapper(items));
    }

    @GetMapping("/calendar/always-open")
    public ResponseEntity<PolicyCalendarPageResponse> findAlwaysOpenPolicies(
            @RequestParam(required = false) String regions,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        RegionFilter filter = resolveRegionFilter(regions, regionCode);
        PolicyCalendarPageResult result =
                policyQueryService.findAlwaysOpen(filter, category, page, Math.min(size, 50));
        return ResponseEntity.ok(PolicyCalendarPageResponse.from(result));
    }

    public record CalendarResponseWrapper(List<PolicyCalendarResponse> items) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidArg(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @GetMapping("/search")
    @Override
    public ResponseEntity<PolicyPageResponse> searchPolicies(
            @RequestParam String keyword,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PolicyPageResult result = policyQueryService.searchPoliciesByKeyword(keyword, status, page, size);
        return ResponseEntity.ok(PolicyPageResponse.from(result));
    }
}
