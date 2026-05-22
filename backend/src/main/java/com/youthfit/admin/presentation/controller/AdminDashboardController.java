package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminDashboardOverviewService;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController implements AdminDashboardApi {

    private final AdminDashboardOverviewService service;

    @GetMapping("/overview")
    @Override
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.ok(service.findOverview()));
    }
}
