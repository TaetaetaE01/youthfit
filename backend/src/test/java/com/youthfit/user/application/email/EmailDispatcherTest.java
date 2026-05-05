package com.youthfit.user.application.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.port.EmailSender;
import com.youthfit.user.application.service.NotificationDispatchService;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import com.youthfit.user.domain.model.AuthProvider;
import com.youthfit.user.domain.model.EmailSendAttempt;
import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.model.NotificationHistory;
import com.youthfit.user.domain.model.NotificationStatus;
import com.youthfit.user.domain.model.NotificationType;
import com.youthfit.user.domain.model.User;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import com.youthfit.user.domain.repository.NotificationHistoryRepository;
import com.youthfit.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("EmailDispatcher")
@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @InjectMocks
    private EmailDispatcher emailDispatcher;

    @Mock private EmailSender emailSender;
    @Mock private NotificationEmailRenderer renderer;
    @Mock private EmailSendAttemptRepository attemptRepository;
    @Mock private NotificationDispatchService dispatchService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Spy  private Clock clock = Clock.fixed(Instant.parse("2026-05-05T00:00:00Z"), ZoneId.of("UTC"));
    @Mock private UserRepository userRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private PolicyRepository policyRepository;

    private User user;
    private Policy policy;
    private NotificationHistory history;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@example.com")
                .nickname("테스터")
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao_1")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        policy = Policy.builder()
                .title("테스트 정책")
                .category(Category.JOBS)
                .applyEnd(LocalDate.of(2026, 6, 30))
                .build();
        ReflectionTestUtils.setField(policy, "id", 10L);

        history = NotificationHistory.pending(1L, 10L, NotificationType.DEADLINE);
        ReflectionTestUtils.setField(history, "id", 999L);
    }

    @Test
    @DisplayName("dispatchDeadline 성공 시: SENT attempt 저장 + markSent 호출")
    void dispatchDeadline_success_savesAttemptAndMarksSent() {
        // given
        given(emailSender.sendDeadlineNotification("test@example.com", policy))
                .willReturn(new EmailSendResult("ses-msg-123", "[YouthFit] 마감"));

        // when
        emailDispatcher.dispatchDeadline(history, user, policy);

        // then
        ArgumentCaptor<EmailSendAttempt> captor = ArgumentCaptor.forClass(EmailSendAttempt.class);
        then(attemptRepository).should().save(captor.capture());
        EmailSendAttempt saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailSendStatus.SENT);
        assertThat(saved.getSesMessageId()).isEqualTo("ses-msg-123");
        assertThat(saved.getSubject()).isEqualTo("[YouthFit] 마감");
        assertThat(saved.getNotificationHistoryId()).isEqualTo(999L);

        then(dispatchService).should().markSent(999L);
    }

    @Test
    @DisplayName("dispatchDeadline 실패 시: FAILED attempt 저장 + markFailed 호출 + 예외 rethrow")
    void dispatchDeadline_failure_savesFailedAttemptAndMarksFailed() {
        // given
        given(renderer.renderDeadline(policy))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html/>", "text"));
        willThrow(new EmailSendException("SES 실패", new RuntimeException("AwsError")))
                .given(emailSender).sendDeadlineNotification("test@example.com", policy);

        // when & then
        assertThatThrownBy(() -> emailDispatcher.dispatchDeadline(history, user, policy))
                .isInstanceOf(EmailSendException.class);

        ArgumentCaptor<EmailSendAttempt> captor = ArgumentCaptor.forClass(EmailSendAttempt.class);
        then(attemptRepository).should().save(captor.capture());
        EmailSendAttempt saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailSendStatus.FAILED);
        assertThat(saved.getErrorCode()).isEqualTo("RuntimeException");
        assertThat(saved.getSesMessageId()).isNull();

        then(dispatchService).should().markFailed(999L, "SES 실패");
        then(dispatchService).should(never()).markSent(any());
    }

    @Test
    @DisplayName("redispatch: SENT 상태 attempt 은 IllegalStateException")
    void redispatch_nonFailed_throwsIllegalState() {
        // given
        EmailSendAttempt attempt = EmailSendAttempt.success(
                999L, 1L, "test@example.com", NotificationType.DEADLINE,
                "ses-id", "[YouthFit] 마감", "{}", java.time.LocalDateTime.now());
        given(attemptRepository.findById(1L)).willReturn(Optional.of(attempt));

        // when & then
        assertThatThrownBy(() -> emailDispatcher.redispatch(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED 상태만 재발송 가능");
    }

    @Test
    @DisplayName("redispatch: FAILED DEADLINE attempt 재발송 시 새 SENT attempt 저장")
    void redispatch_FAILED_attempt_새_row_생성() {
        // given — original FAILED attempt
        EmailSendAttempt original = EmailSendAttempt.failure(
                100L, 7L, "u@ex.com", NotificationType.DEADLINE,
                "subj", "{\"policyId\":42}", "SES_X", "boom",
                LocalDateTime.of(2026, 5, 5, 9, 0));
        given(attemptRepository.findById(99L)).willReturn(Optional.of(original));

        User user = mock(User.class);
        given(user.getId()).willReturn(7L);
        given(user.getEmail()).willReturn("u@ex.com");
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        NotificationHistory history = mock(NotificationHistory.class);
        given(history.getId()).willReturn(100L);
        given(historyRepository.findById(100L)).willReturn(Optional.of(history));

        Policy policy = mock(Policy.class);
        given(policy.getId()).willReturn(42L);
        given(policyRepository.findById(42L)).willReturn(Optional.of(policy));

        given(emailSender.sendDeadlineNotification("u@ex.com", policy))
                .willReturn(new EmailSendResult("ses-msg-NEW", "[YouthFit] 마감 임박"));

        EmailSendAttempt savedSpy = mock(EmailSendAttempt.class);
        given(savedSpy.getId()).willReturn(123L);
        given(attemptRepository.findTopByOrderByIdDesc()).willReturn(savedSpy);

        // when
        Long result = emailDispatcher.redispatch(99L);

        // then
        assertThat(result).isEqualTo(123L);

        ArgumentCaptor<EmailSendAttempt> captor = ArgumentCaptor.forClass(EmailSendAttempt.class);
        verify(attemptRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(a -> "ses-msg-NEW".equals(a.getSesMessageId())
                               && a.getStatus() == EmailSendStatus.SENT);
        verify(dispatchService).markSent(100L);
    }
}
