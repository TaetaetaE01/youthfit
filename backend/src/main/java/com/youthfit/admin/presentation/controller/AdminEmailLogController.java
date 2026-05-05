package com.youthfit.admin.presentation.controller;

import com.youthfit.admin.presentation.dto.request.EmailAttemptListQuery;
import com.youthfit.admin.presentation.dto.response.EmailAttemptDailyStatsResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptDetailResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptKpiResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptPreviewResponse;
import com.youthfit.admin.presentation.dto.response.EmailAttemptSummaryResponse;
import com.youthfit.common.response.ApiResponse;
import com.youthfit.user.application.email.EmailDispatcher;
import com.youthfit.user.application.email.EmailSendAttemptQueryService;
import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/email-attempts")
@RequiredArgsConstructor
public class AdminEmailLogController implements AdminEmailLogApi {

    private final EmailSendAttemptQueryService queryService;
    private final EmailDispatcher dispatcher;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<Page<EmailAttemptSummaryResponse>>> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) NotificationType emailType,
            @RequestParam(required = false) String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<EmailSendStatus> statusList = parseStatuses(statuses);
        EmailAttemptListQuery query = new EmailAttemptListQuery(
                from, to, statusList, emailType, recipient, page, size);
        return ResponseEntity.ok(ApiResponse.ok(queryService.list(query)));
    }

    @GetMapping("/{id}")
    @Override
    public ResponseEntity<ApiResponse<EmailAttemptDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.detail(id)));
    }

    @GetMapping("/stats/daily")
    @Override
    public ResponseEntity<ApiResponse<List<EmailAttemptDailyStatsResponse>>> dailyStats(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.dailyStats(from, to)));
    }

    @GetMapping("/stats/kpi")
    @Override
    public ResponseEntity<ApiResponse<EmailAttemptKpiResponse>> kpi() {
        return ResponseEntity.ok(ApiResponse.ok(queryService.kpi()));
    }

    @GetMapping("/{id}/preview")
    @Override
    public ResponseEntity<ApiResponse<EmailAttemptPreviewResponse>> preview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.preview(id)));
    }

    @PostMapping("/{id}/redispatch")
    @Override
    public ResponseEntity<ApiResponse<Map<String, Long>>> redispatch(@PathVariable Long id) {
        Long newId = dispatcher.redispatch(id);
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("newAttemptId", newId)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("ILLEGAL_STATE", e.getMessage()));
    }

    private List<EmailSendStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(EmailSendStatus::valueOf)
                .toList();
    }
}
