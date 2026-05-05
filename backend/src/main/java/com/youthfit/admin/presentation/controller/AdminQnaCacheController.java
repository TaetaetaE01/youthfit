package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.application.service.AdminQnaCacheService;
import com.youthfit.admin.presentation.dto.request.QnaCacheLookupListQuery;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupDetailResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupKpiResponse;
import com.youthfit.admin.presentation.dto.response.QnaCacheLookupSummaryResponse;
import com.youthfit.common.response.ApiResponse;
import com.youthfit.qna.domain.model.LookupResultType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/qna-cache")
public class AdminQnaCacheController implements AdminQnaCacheApi {

    private final AdminQnaCacheService service;

    @Override
    @GetMapping("/kpi")
    public ResponseEntity<ApiResponse<QnaCacheLookupKpiResponse>> kpi() {
        return ResponseEntity.ok(ApiResponse.ok(service.kpi()));
    }

    @Override
    @GetMapping("/daily-stats")
    public ResponseEntity<ApiResponse<List<QnaCacheLookupDailyStatsResponse>>> dailyStats(
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(ApiResponse.ok(service.dailyStats(days)));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<Page<QnaCacheLookupSummaryResponse>>> list(
            @RequestParam(required = false) LookupResultType result,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.list(new QnaCacheLookupListQuery(result, policyId, from, to, page, size))
        ));
    }

    @Override
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<QnaCacheLookupDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.detail(id)));
    }

    @Override
    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(required = false) LookupResultType result,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            HttpServletResponse response
    ) {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"qna-cache-lookup.csv\"");
        try (PrintWriter w = response.getWriter()) {
            service.exportCsv(
                    new QnaCacheLookupListQuery(result, policyId, from, to, 0, Integer.MAX_VALUE),
                    w
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to write CSV response", e);
        }
    }
}
