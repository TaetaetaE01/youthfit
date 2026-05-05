package com.youthfit.user.application.email;

import com.youthfit.admin.presentation.dto.request.EmailAttemptListQuery;
import com.youthfit.admin.presentation.dto.response.*;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendAttemptNotFoundException;
import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailSendAttemptQueryService {

    private final EmailSendAttemptRepository attemptRepository;
    private final NotificationEmailRenderer renderer;
    private final PolicyRepository policyRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public Page<EmailAttemptSummaryResponse> list(EmailAttemptListQuery q) {
        LocalDateTime from = q.from() == null
            ? LocalDateTime.now(clock).minusDays(30)
            : q.from().atStartOfDay();
        LocalDateTime to = q.to() == null
            ? LocalDateTime.now(clock)
            : q.to().atTime(LocalTime.MAX);
        return attemptRepository.search(
            from, to, q.statuses(), q.emailType(), q.recipient(),
            PageRequest.of(q.page(), q.size())
        ).map(this::toSummary);
    }

    public EmailAttemptDetailResponse detail(Long id) {
        EmailSendAttempt a = attemptRepository.findById(id)
            .orElseThrow(() -> new EmailSendAttemptNotFoundException(id));
        return new EmailAttemptDetailResponse(
            a.getId(), a.getNotificationHistoryId(), a.getRecipientEmail(), a.getRecipientUserId(),
            a.getEmailType().name(), a.getSubject(), a.getInputPayloadJson(),
            a.getSesMessageId(), a.getStatus().name(),
            a.getErrorCode(), a.getErrorMessage(), a.getBounceType(),
            a.getSentAt(), a.getUpdatedAt());
    }

    public List<EmailAttemptDailyStatsResponse> dailyStats(LocalDate from, LocalDate to) {
        List<Object[]> rows = attemptRepository.aggregateDaily(
            from.atStartOfDay(), to.atTime(LocalTime.MAX));
        Map<String, Map<String, Long>> byDate = new TreeMap<>();
        for (Object[] row : rows) {
            String date = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            byDate.computeIfAbsent(date, k -> new HashMap<>()).put(status, count);
        }
        return byDate.entrySet().stream().map(e -> new EmailAttemptDailyStatsResponse(
            e.getKey(),
            e.getValue().getOrDefault("SENT", 0L),
            e.getValue().getOrDefault("DELIVERED", 0L),
            e.getValue().getOrDefault("BOUNCED", 0L),
            e.getValue().getOrDefault("COMPLAINED", 0L),
            e.getValue().getOrDefault("FAILED", 0L)
        )).toList();
    }

    public EmailAttemptKpiResponse kpi() {
        LocalDateTime now = LocalDateTime.now(clock);
        EmailAttemptKpiResponse.Bucket today = bucket(now.toLocalDate().atStartOfDay(), now);
        LocalDateTime weekStart = now.toLocalDate().minusDays(7).atStartOfDay();
        EmailAttemptKpiResponse.Bucket week = bucket(weekStart, now);
        long total = week.sent() + week.delivered() + week.bounced() + week.failed();
        double rate = total == 0 ? 0.0 : (double)(week.sent() + week.delivered()) / total;
        return new EmailAttemptKpiResponse(today, week, rate);
    }

    public EmailAttemptPreviewResponse preview(Long id) {
        EmailSendAttempt a = attemptRepository.findById(id)
            .orElseThrow(() -> new EmailSendAttemptNotFoundException(id));
        return switch (a.getEmailType()) {
            case DEADLINE -> {
                Long policyId = readPolicyId(a.getInputPayloadJson());
                Policy p = policyRepository.findById(policyId)
                    .orElseThrow(() -> new IllegalStateException("Policy 없음: " + policyId));
                var c = renderer.renderDeadline(p);
                yield new EmailAttemptPreviewResponse(c.subject(), c.htmlBody(), c.textBody());
            }
            case RECOMMENDATION -> {
                List<Long> ids = readPolicyIds(a.getInputPayloadJson());
                List<Policy> ps = policyRepository.findAllById(ids);
                var c = renderer.renderRecommendation(ps);
                yield new EmailAttemptPreviewResponse(c.subject(), c.htmlBody(), c.textBody());
            }
        };
    }

    private EmailAttemptKpiResponse.Bucket bucket(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = attemptRepository.aggregateDaily(from, to);
        long sent = 0, delivered = 0, bounced = 0, failed = 0;
        for (Object[] row : rows) {
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            switch (status) {
                case "SENT" -> sent += count;
                case "DELIVERED" -> delivered += count;
                case "BOUNCED" -> bounced += count;
                case "FAILED" -> failed += count;
            }
        }
        return new EmailAttemptKpiResponse.Bucket(sent, delivered, bounced, failed);
    }

    private EmailAttemptSummaryResponse toSummary(EmailSendAttempt a) {
        return new EmailAttemptSummaryResponse(
            a.getId(), a.getRecipientEmail(), a.getEmailType().name(),
            a.getStatus().name(), a.getSubject(), a.getSentAt(), a.getUpdatedAt(),
            a.getSesMessageId());
    }

    private Long readPolicyId(String json) {
        try { return objectMapper.readTree(json).path("policyId").asLong(); }
        catch (Exception e) { throw new IllegalStateException("input_payload 파싱 실패", e); }
    }

    private List<Long> readPolicyIds(String json) {
        try {
            var node = objectMapper.readTree(json).path("policyIds");
            List<Long> out = new ArrayList<>();
            node.forEach(n -> out.add(n.asLong()));
            return out;
        } catch (Exception e) { throw new IllegalStateException("input_payload 파싱 실패", e); }
    }
}
