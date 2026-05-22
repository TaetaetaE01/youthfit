package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.dto.result.DashboardActionItemResult;
import com.youthfit.admin.application.dto.result.DashboardAreaStatusResult;
import com.youthfit.admin.application.dto.result.DashboardOverviewResult;
import com.youthfit.admin.application.service.AdminDashboardOverviewService;
import com.youthfit.admin.presentation.dto.response.DashboardActionItemResponse;
import com.youthfit.admin.presentation.dto.response.DashboardAreaStatusResponse;
import com.youthfit.admin.presentation.dto.response.DashboardOverviewResponse;
import com.youthfit.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController implements AdminDashboardApi {

    private final AdminDashboardOverviewService service;

    @GetMapping("/overview")
    @Override
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview() {
        DashboardOverviewResult result = service.findOverview();
        return ResponseEntity.ok(ApiResponse.ok(toResponse(result)));
    }

    private static DashboardOverviewResponse toResponse(DashboardOverviewResult result) {
        List<DashboardActionItemResponse> actionItems = result.actionItems().stream()
                .map(AdminDashboardController::toActionItemResponse)
                .toList();
        List<DashboardAreaStatusResponse> areas = result.areas().stream()
                .map(AdminDashboardController::toAreaStatusResponse)
                .toList();
        return new DashboardOverviewResponse(result.generatedAt(), actionItems, areas);
    }

    private static DashboardActionItemResponse toActionItemResponse(DashboardActionItemResult r) {
        return new DashboardActionItemResponse(
                r.code(),
                r.severity().name(),
                r.title(),
                r.detail(),
                r.deeplink(),
                r.detectedAt()
        );
    }

    private static DashboardAreaStatusResponse toAreaStatusResponse(DashboardAreaStatusResult r) {
        return new DashboardAreaStatusResponse(
                r.key(),
                r.label(),
                r.status().name(),
                r.summary(),
                r.sparkline(),
                r.deeplink()
        );
    }
}
