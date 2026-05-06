package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminLlmCostService;
import com.youthfit.admin.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm-cost")
@RequiredArgsConstructor
public class AdminLlmCostController implements AdminLlmCostApi {

    private final AdminLlmCostService service;

    @Override
    @GetMapping("/kpi")
    public ResponseEntity<LlmCostKpiResponse> getKpi() {
        return ResponseEntity.ok(service.getKpi());
    }

    @Override
    @GetMapping("/series")
    public ResponseEntity<LlmCostSeriesResponse> getSeries(
            @RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getSeries(range));
    }

    @Override
    @GetMapping("/by-module")
    public ResponseEntity<List<LlmCostModuleDailyResponse>> getDailyByModule(
            @RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getDailyByModule(range));
    }

    @Override
    @GetMapping("/by-model")
    public ResponseEntity<List<LlmCostModelSummaryResponse>> getModelSummary(
            @RequestParam(required = false, defaultValue = "7d") String range) {
        return ResponseEntity.ok(service.getModelSummary(range));
    }
}
