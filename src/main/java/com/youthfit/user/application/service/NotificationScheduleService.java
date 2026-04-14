package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.model.Bookmark;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.BookmarkRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import com.youthfit.user.domain.repository.UserRepository;
import com.youthfit.user.domain.service.NotificationTargetResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {

    private static final String NOTIFICATION_TYPE_DEADLINE = "DEADLINE";

    private final NotificationSettingRepository notificationSettingRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final EmailSender emailSender;

    @Transactional
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

            List<Bookmark> bookmarks = bookmarkRepository.findAllByUserId(userId);
            for (Bookmark bookmark : bookmarks) {
                Policy policy = policyRepository.findById(bookmark.getPolicyId()).orElse(null);
                if (policy == null) {
                    continue;
                }

                if (!NotificationTargetResolver.shouldNotify(policy, setting.getDaysBeforeDeadline(), today)) {
                    continue;
                }

                if (notificationHistoryRepository.existsByUserIdAndPolicyIdAndNotificationType(
                        userId, policy.getId(), NOTIFICATION_TYPE_DEADLINE)) {
                    continue;
                }

                emailSender.sendDeadlineNotification(user.getEmail(), policy);
                notificationHistoryRepository.save(
                        new NotificationHistory(userId, policy.getId(), NOTIFICATION_TYPE_DEADLINE));
            }
        }
    }
}
