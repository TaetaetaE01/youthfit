package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LoggingEmailSender")
class LoggingEmailSenderTest {

    private final LoggingEmailSender loggingEmailSender = new LoggingEmailSender();

    @Test
    @DisplayName("마감일 알림 이메일 발송 시 예외 없이 로그를 출력한다")
    void sendDeadlineNotification_logsWithoutException() {
        // given
        Policy policy = Policy.builder()
                .title("청년 취업 지원")
                .category(Category.JOBS)
                .applyEnd(LocalDate.of(2026, 6, 30))
                .build();
        ReflectionTestUtils.setField(policy, "id", 1L);

        // when & then
        assertThatCode(() ->
                loggingEmailSender.sendDeadlineNotification("test@example.com", policy)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마감일이 null인 정책도 예외 없이 처리한다")
    void sendDeadlineNotification_nullApplyEnd_noException() {
        // given
        Policy policy = Policy.builder()
                .title("상시 모집 정책")
                .category(Category.WELFARE)
                .build();
        ReflectionTestUtils.setField(policy, "id", 2L);

        // when & then
        assertThatCode(() ->
                loggingEmailSender.sendDeadlineNotification("user@example.com", policy)
        ).doesNotThrowAnyException();
    }
}
