package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.email.EmailDispatcher;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.NotificationTargetResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final PolicyNotificationSubscriptionRepository subscriptionRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final NotificationDispatchService dispatchService;
    private final EmailDispatcher emailDispatcher;

    /**
     * 메서드 레벨 @Transactional 제거 — SES 외부 IO 동안 DB 커넥션 점유 방지.
     * 상태 전이는 NotificationDispatchService 의 REQUIRES_NEW 메서드들이 담당.
     */
    public void sendDeadlineNotifications() {
        LocalDate today = LocalDate.now();
        List<NotificationSetting> activeSettings = notificationSettingRepository.findAllByEmailEnabled(true);

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("알림 설정 userId={} 에 해당하는 사용자를 찾을 수 없습니다", userId);
                continue;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }

            List<PolicyNotificationSubscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
            for (PolicyNotificationSubscription subscription : subscriptions) {
                processOnePolicy(setting, user, subscription, today);
            }
        }
    }

    private void processOnePolicy(NotificationSetting setting, User user,
                                  PolicyNotificationSubscription subscription, LocalDate today) {
        Long userId = user.getId();
        Long policyId = subscription.getPolicyId();
        Policy policy = policyRepository.findById(policyId).orElse(null);
        if (policy == null) return;
        if (!NotificationTargetResolver.shouldNotify(policy, setting.getDaysBeforeDeadline(), today)) {
            return;
        }

        NotificationHistory history = dispatchService.reservePending(userId, policyId, NotificationType.DEADLINE);
        if (history == null) {
            log.debug("마감일 알림 PENDING 충돌 — skip userId={} policyId={}", userId, policyId);
            return;
        }

        try {
            emailDispatcher.dispatchDeadline(history, user, policy);
            log.info("마감일 알림 발송 완료 userId={} policyId={} historyId={}",
                    userId, policyId, history.getId());
        } catch (EmailSendException e) {
            // dispatcher 가 attempt + markFailed 처리 후 rethrow — 스케줄러는 swallow 하고 다음 항목 진행
            log.error("마감일 알림 발송 실패 userId={} policyId={} historyId={}",
                    userId, policyId, history.getId(), e);
        } catch (Exception e) {
            // dispatchService.markFailed 도 실패한 케이스 등 — PENDING 으로 잔존
            log.error("마감일 알림 처리 중 예외 userId={} policyId={}", userId, policyId, e);
        }
    }
}
