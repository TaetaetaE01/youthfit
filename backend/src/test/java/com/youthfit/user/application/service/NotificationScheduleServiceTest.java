package com.youthfit.user.application.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.PolicyNotificationSubscription;
import com.youthfit.user.domain.model.User;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@DisplayName("NotificationScheduleService")
@ExtendWith(MockitoExtension.class)
class NotificationScheduleServiceTest {

    @InjectMocks
    private NotificationScheduleService notificationScheduleService;

    @Mock private NotificationSettingRepository notificationSettingRepository;
    @Mock private PolicyNotificationSubscriptionRepository subscriptionRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationDispatchService dispatchService;
    @Mock private EmailSender emailSender;

    @Nested
    @DisplayName("sendDeadlineNotifications")
    class SendDeadlineNotifications {

        @Test
        @DisplayName("마감 임박 정책에 대해 reservePending → 발송 → markSent 흐름")
        void eligiblePolicy_reservesAndSends() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));
            NotificationHistory pending = NotificationHistory.pending(1L, 10L, NotificationType.DEADLINE);
            ReflectionTestUtils.setField(pending, "id", 999L);

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(pending);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should().sendDeadlineNotification(eq("test@example.com"), eq(policy));
            then(dispatchService).should().markSent(999L);
        }

        @Test
        @DisplayName("reservePending 이 null 이면 발송 안 함 (이미 처리됨)")
        void reservePendingNull_skipsSend() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(null);

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("EmailSendException 발생 시 markFailed 호출")
        void emailSendException_callsMarkFailed() {
            // given
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            PolicyNotificationSubscription subscription = new PolicyNotificationSubscription(1L, 10L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(3));
            NotificationHistory pending = NotificationHistory.pending(1L, 10L, NotificationType.DEADLINE);
            ReflectionTestUtils.setField(pending, "id", 999L);

            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L)).willReturn(List.of(subscription));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));
            given(dispatchService.reservePending(1L, 10L, NotificationType.DEADLINE)).willReturn(pending);
            willThrow(new EmailSendException("SES 발송 실패: test@example.com", new RuntimeException()))
                    .given(emailSender).sendDeadlineNotification(any(), any());

            // when
            notificationScheduleService.sendDeadlineNotifications();

            // then
            then(dispatchService).should().markFailed(eq(999L), any());
            then(dispatchService).should(never()).markSent(any());
        }

        @Test
        @DisplayName("사용자 없음 → 건너뛰기")
        void userNotFound_skips() {
            given(notificationSettingRepository.findAllByEmailEnabled(true))
                    .willReturn(List.of(new NotificationSetting(1L)));
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            notificationScheduleService.sendDeadlineNotifications();

            then(subscriptionRepository).should(never()).findAllByUserId(any());
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("정책 없음 → 건너뛰기")
        void policyNotFound_skips() {
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L))
                    .willReturn(List.of(new PolicyNotificationSubscription(1L, 10L)));
            given(policyRepository.findById(10L)).willReturn(Optional.empty());

            notificationScheduleService.sendDeadlineNotifications();

            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }

        @Test
        @DisplayName("마감 먼 정책 → 건너뛰기")
        void farDeadline_skips() {
            NotificationSetting setting = new NotificationSetting(1L);
            User user = createUser(1L);
            Policy policy = createOpenPolicyWithDeadline(10L, LocalDate.now().plusDays(30));
            given(notificationSettingRepository.findAllByEmailEnabled(true)).willReturn(List.of(setting));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(subscriptionRepository.findAllByUserId(1L))
                    .willReturn(List.of(new PolicyNotificationSubscription(1L, 10L)));
            given(policyRepository.findById(10L)).willReturn(Optional.of(policy));

            notificationScheduleService.sendDeadlineNotifications();

            then(dispatchService).should(never()).reservePending(any(), any(), any());
            then(emailSender).should(never()).sendDeadlineNotification(any(), any());
        }
    }

    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_" + id)
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
