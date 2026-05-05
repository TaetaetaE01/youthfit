package com.youthfit.user.application.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationDispatchService;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatcher {

    private final EmailSender emailSender;
    private final NotificationEmailRenderer renderer;
    private final EmailSendAttemptRepository attemptRepository;
    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void dispatchDeadline(NotificationHistory history, User user, Policy policy) {
        String inputJson = toJson(Map.of("policyId", policy.getId()));
        try {
            EmailSendResult result = emailSender.sendDeadlineNotification(user.getEmail(), policy);
            attemptRepository.save(EmailSendAttempt.success(
                    history.getId(), user.getId(), user.getEmail(),
                    NotificationType.DEADLINE,
                    result.sesMessageId(), result.subject(),
                    inputJson,
                    LocalDateTime.now(clock)));
            dispatchService.markSent(history.getId());
        } catch (EmailSendException e) {
            String fallbackSubject = renderer.renderDeadline(policy).subject();
            attemptRepository.save(EmailSendAttempt.failure(
                    history.getId(), user.getId(), user.getEmail(),
                    NotificationType.DEADLINE,
                    fallbackSubject,
                    inputJson,
                    errorCodeOf(e),
                    e.getMessage(),
                    LocalDateTime.now(clock)));
            dispatchService.markFailed(history.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void dispatchRecommendation(NotificationHistory history, User user, List<Policy> policies) {
        List<Long> policyIds = policies.stream().map(Policy::getId).toList();
        String inputJson = toJson(Map.of("policyIds", policyIds));
        try {
            EmailSendResult result = emailSender.sendRecommendationNotification(user.getEmail(), policies);
            attemptRepository.save(EmailSendAttempt.success(
                    history.getId(), user.getId(), user.getEmail(),
                    NotificationType.RECOMMENDATION,
                    result.sesMessageId(), result.subject(),
                    inputJson,
                    LocalDateTime.now(clock)));
            dispatchService.markSent(history.getId());
        } catch (EmailSendException e) {
            String fallbackSubject = renderer.renderRecommendation(policies).subject();
            attemptRepository.save(EmailSendAttempt.failure(
                    history.getId(), user.getId(), user.getEmail(),
                    NotificationType.RECOMMENDATION,
                    fallbackSubject,
                    inputJson,
                    errorCodeOf(e),
                    e.getMessage(),
                    LocalDateTime.now(clock)));
            dispatchService.markFailed(history.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Long redispatch(Long attemptId) {
        EmailSendAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("EmailSendAttempt 를 찾을 수 없습니다: " + attemptId));
        if (attempt.getStatus() != EmailSendStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태만 재발송 가능. 현재: " + attempt.getStatus());
        }
        throw new UnsupportedOperationException("redispatch end-to-end 는 RedispatchService 에서 구현됨 (Task 9)");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 직렬화 실패: {}", e.getMessage());
            return "{}";
        }
    }

    private String errorCodeOf(EmailSendException e) {
        Throwable cause = e.getCause();
        return cause != null ? cause.getClass().getSimpleName() : "EMAIL_SEND_ERROR";
    }
}
