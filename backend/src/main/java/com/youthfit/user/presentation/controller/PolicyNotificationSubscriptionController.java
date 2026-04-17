package com.youthfit.user.presentation.controller;

import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.dto.result.PolicyNotificationSubscriptionResult;
import com.youthfit.user.application.service.PolicyNotificationSubscriptionService;
import com.youthfit.user.presentation.dto.response.PolicyNotificationSubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies/{policyId}/subscription")
@RequiredArgsConstructor
public class PolicyNotificationSubscriptionController implements PolicyNotificationSubscriptionApi {

    private final PolicyNotificationSubscriptionService subscriptionService;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<PolicyNotificationSubscriptionResponse>> findSubscription(
            Authentication authentication,
            @PathVariable Long policyId) {
        Long userId = (Long) authentication.getPrincipal();
        PolicyNotificationSubscriptionResult result = subscriptionService.findSubscription(userId, policyId);
        return ResponseEntity.ok(ApiResponse.ok(PolicyNotificationSubscriptionResponse.from(result)));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<PolicyNotificationSubscriptionResponse>> subscribe(
            Authentication authentication,
            @PathVariable Long policyId) {
        Long userId = (Long) authentication.getPrincipal();
        PolicyNotificationSubscriptionResult result = subscriptionService.subscribe(userId, policyId);
        return ResponseEntity.ok(ApiResponse.ok(PolicyNotificationSubscriptionResponse.from(result)));
    }

    @DeleteMapping
    @Override
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            Authentication authentication,
            @PathVariable Long policyId) {
        Long userId = (Long) authentication.getPrincipal();
        subscriptionService.unsubscribe(userId, policyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
