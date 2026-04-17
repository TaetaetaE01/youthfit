package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.model.*;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.NotificationSettingRepository;
import com.youthfit.user.domain.repository.PolicyNotificationSubscriptionRepository;
import com.youthfit.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("NotificationScheduleService")
@ExtendWith(MockitoExtension.class)
class NotificationScheduleServiceTest {

    @InjectMocks
    private NotificationScheduleService notificationScheduleService;

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private PolicyNotificationSubscriptionRepository subscriptionRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Mock
    private EmailSender emailSender;

    @Nested
    @DisplayName("sendDeadlineNotifications")
    class SendDeadlineNotifications {

        @Test
        @DisplayName("마감 임박 구독 정책에 대해 이메일을 발송하고 이력을 저장한다")
        void eligiblePolicy_sendsEmailAndSavesHistory() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));

            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(notificationHistoryRepository.existsByUserIdAndPolicyIdAndNotificationType(1L, 10L, "DEADLINE"))
                    .willReturn(false);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should().sendDeadlineNotification(eq("test@example.com"), eq(policy));
            then(notificationHistoryRepository).should().save(any(NotificationHistory.class));
        }

        @Test
        @DisplayName("이미 발송 이력이 있으면 중복 발송하지 않는다")
        void alreadySent_skipsEmail() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));

            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(notificationHistoryRepository.existsByUserIdAndPolicyIdAndNotificationType(1L, 10L, "DEADLINE"))
                    .willReturn(true);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 건너뛴다")
        void userNotFound_skips() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(subscriptionRepository).should(never()).findAllByUserId(any());
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("정책을 찾을 수 없으면 건너뛴다")
        void policyNotFound_skips() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);

            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.empty());

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("마감일이 먼 정책은 알림 대상에서 제외된다")
        void farDeadline_skips() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(30));

            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("활성 설정이 없으면 아무 작업도 하지 않는다")
        void noActiveSettings_doesNothing() {
            // given
            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of());

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }
    }

    // ── 헬퍼 메서드 ──

    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_123")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Policy createOpenPolicyWithDeadline(Long id, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .category(Category.JOBS)
                .applyStart(LocalDate.now().minusDays(30))
                .applyEnd(applyEnd)
                .build();
        policy.open();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
