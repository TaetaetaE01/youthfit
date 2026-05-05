package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import com.youthfit.user.application.email.EmailSendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("LoggingEmailSender")
class LoggingEmailSenderTest {

    private NotificationEmailRenderer renderer;
    private LoggingEmailSender loggingEmailSender;

    @BeforeEach
    void setUp() {
        renderer = mock(NotificationEmailRenderer.class);
        loggingEmailSender = new LoggingEmailSender(renderer);

        given(renderer.renderDeadline(any()))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html>...</html>", "text"));
        given(renderer.renderRecommendation(any()))
                .willReturn(new EmailContent("[YouthFit] 추천", "<html>...</html>", "text"));
    }

    @Test
    @DisplayName("마감일 알림 발송 시 예외 없이 로그를 출력하고 EmailSendResult 를 반환한다")
    void sendDeadlineNotification_logsWithoutException() {
        // given
        Policy policy = createPolicy(1L, "청년 취업 지원", LocalDate.of(2026, 6, 30));

        // when
        EmailSendResult result = loggingEmailSender.sendDeadlineNotification("test@example.com", policy);

        // then
        assertThatCode(() ->
                loggingEmailSender.sendDeadlineNotification("test@example.com", policy)
        ).doesNotThrowAnyException();
        assertThat(result.sesMessageId()).startsWith("logging-");
        assertThat(result.subject()).isEqualTo("[YouthFit] 마감");
    }

    @Test
    @DisplayName("추천 알림 발송 시 예외 없이 로그를 출력하고 EmailSendResult 를 반환한다")
    void sendRecommendationNotification_logsWithoutException() {
        // given
        Policy policy = createPolicy(1L, "정책1", LocalDate.of(2026, 6, 30));

        // when
        EmailSendResult result = loggingEmailSender.sendRecommendationNotification("test@example.com", List.of(policy));

        // then
        assertThatCode(() ->
                loggingEmailSender.sendRecommendationNotification("test@example.com", List.of(policy))
        ).doesNotThrowAnyException();
        assertThat(result.sesMessageId()).startsWith("logging-");
        assertThat(result.subject()).isEqualTo("[YouthFit] 추천");
    }

    private Policy createPolicy(Long id, String title, LocalDate applyEnd) {
        Policy policy = Policy.builder()
                .title(title)
                .category(Category.JOBS)
                .applyEnd(applyEnd)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
