package com.youthfit.user.application.email;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
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
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final UserRepository userRepository;
    private final NotificationHistoryRepository historyRepository;
    private final PolicyRepository policyRepository;

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
        EmailSendAttempt original = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("EmailSendAttempt 를 찾을 수 없습니다: " + attemptId));
        if (original.getStatus() != EmailSendStatus.FAILED) {
            throw new IllegalStateException("FAILED 상태만 재발송 가능. 현재: " + original.getStatus());
        }

        User user = userRepository.findById(original.getRecipientUserId())
                .orElseThrow(() -> new IllegalStateException("User 없음: " + original.getRecipientUserId()));

        NotificationHistory history = historyRepository.findById(original.getNotificationHistoryId())
                .orElseThrow(() -> new IllegalStateException("History 없음: " + original.getNotificationHistoryId()));

        switch (original.getEmailType()) {
            case DEADLINE -> {
                Long policyId = readPolicyId(original.getInputPayloadJson());
                Policy policy = policyRepository.findById(policyId)
                        .orElseThrow(() -> new IllegalStateException("Policy 없음: " + policyId));
                try {
                    dispatchDeadline(history, user, policy);
                } catch (EmailSendException ignored) {
                    // attempt was already saved by dispatchDeadline; just continue
                }
            }
            case RECOMMENDATION -> {
                List<Long> policyIds = readPolicyIds(original.getInputPayloadJson());
                List<Policy> policies = policyRepository.findAllById(policyIds);
                try {
                    dispatchRecommendation(history, user, policies);
                } catch (EmailSendException ignored) {
                    // attempt was already saved by dispatchRecommendation; just continue
                }
            }
        }

        return attemptRepository.findTopByOrderByIdDesc().getId();
    }

    private Long readPolicyId(String json) {
        try {
            return objectMapper.readTree(json).path("policyId").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("input_payload 파싱 실패", e);
        }
    }

    private List<Long> readPolicyIds(String json) {
        try {
            var node = objectMapper.readTree(json).path("policyIds");
            List<Long> out = new ArrayList<>();
            node.forEach(n -> out.add(n.asLong()));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("input_payload 파싱 실패", e);
        }
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
