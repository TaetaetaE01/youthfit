package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminIngestionService;
import com.youthfit.admin.presentation.dto.response.IngestionDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureDetailResponse;
import com.youthfit.admin.presentation.dto.response.IngestionFailureSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionKpiResponse;
import com.youthfit.admin.presentation.dto.response.IngestionRetryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionSourceSummaryResponse;
import com.youthfit.admin.presentation.dto.response.IngestionStaleSourceResponse;
import com.youthfit.ingestion.domain.model.FailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
@RequiredArgsConstructor
public class AdminIngestionController implements AdminIngestionApi {

    private final AdminIngestionService service;

    @Override
    @GetMapping("/kpi")
    public ResponseEntity<IngestionKpiResponse> getKpi() {
        return ResponseEntity.ok(service.getKpi());
    }

    @Override
    @GetMapping("/daily-stats")
    public ResponseEntity<List<IngestionDailyStatsResponse>> getDailyStats(
            @RequestParam(required = false, defaultValue = "14") int days) {
        return ResponseEntity.ok(service.getDailyStats(days));
    }

    @Override
    @GetMapping("/sources")
    public ResponseEntity<List<IngestionSourceSummaryResponse>> getSourceSummaries() {
        return ResponseEntity.ok(service.getSourceSummaries());
    }

    @Override
    @GetMapping("/stale-sources")
    public ResponseEntity<List<IngestionStaleSourceResponse>> getStaleSources() {
        return ResponseEntity.ok(service.getStaleSources());
    }

    @Override
    @GetMapping("/failures")
    public ResponseEntity<Page<IngestionFailureSummaryResponse>> searchFailures(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) FailureReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(service.searchFailures(source, reason, from, to, page, size));
    }

    @Override
    @GetMapping("/failures/{id:\\d+}")
    public ResponseEntity<IngestionFailureDetailResponse> getFailureDetail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getFailureDetail(id));
    }

    @Override
    @PostMapping("/failures/{id:\\d+}/retry")
    public ResponseEntity<IngestionRetryResponse> retryFailure(@PathVariable Long id) {
        return ResponseEntity.ok(service.retryFailure(id));
    }
}
