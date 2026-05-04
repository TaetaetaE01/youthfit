package com.youthfit.user.infrastructure.email;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.application.dto.result.EmailContent;
import com.youthfit.user.application.service.NotificationEmailRenderer;
import com.youthfit.user.domain.exception.EmailSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("SesEmailSender")
@ExtendWith(MockitoExtension.class)
class SesEmailSenderTest {

    @Mock
    private SesV2Client sesClient;

    @Mock
    private NotificationEmailRenderer renderer;

    private SesEmailSender sesEmailSender;

    @BeforeEach
    void setUp() {
        sesEmailSender = new SesEmailSender(sesClient, renderer,
                "noreply@youthfit.example.com", "YouthFit");
    }

    @Test
    @DisplayName("sendDeadlineNotification: SendEmailRequest 가 정확히 빌드된다")
    void sendDeadlineNotification_buildsRequestCorrectly() {
        // given
        Policy policy = createPolicy(10L);
        given(renderer.renderDeadline(any()))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html>마감</html>", "마감 텍스트"));

        // when
        sesEmailSender.sendDeadlineNotification("user@example.com", policy);

        // then
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        then(sesClient).should().sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertThat(req.fromEmailAddress()).contains("noreply@youthfit.example.com");
        assertThat(req.fromEmailAddress()).contains("YouthFit");
        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.content().simple().subject().data()).contains("마감");
        assertThat(req.content().simple().body().html().data()).contains("마감");
        assertThat(req.content().simple().body().text().data()).contains("마감");
    }

    @Test
    @DisplayName("sendRecommendationNotification: SendEmailRequest 가 정확히 빌드된다")
    void sendRecommendationNotification_buildsRequestCorrectly() {
        // given
        Policy policy = createPolicy(10L);
        given(renderer.renderRecommendation(any()))
                .willReturn(new EmailContent("[YouthFit] 추천", "<html>추천</html>", "추천 텍스트"));

        // when
        sesEmailSender.sendRecommendationNotification("user@example.com", List.of(policy));

        // then
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        then(sesClient).should().sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.content().simple().subject().data()).contains("추천");
    }

    @Test
    @DisplayName("SES 호출이 SesV2Exception 을 던지면 EmailSendException 으로 변환")
    void sesV2Exception_translatedToEmailSendException() {
        // given
        Policy policy = createPolicy(10L);
        given(renderer.renderDeadline(any()))
                .willReturn(new EmailContent("[YouthFit] 마감", "<html>마감</html>", "마감 텍스트"));
        willThrow(SesV2Exception.builder().message("AccessDenied").build())
                .given(sesClient).sendEmail(any(SendEmailRequest.class));

        // when & then
        assertThatThrownBy(() -> sesEmailSender.sendDeadlineNotification("user@example.com", policy))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("user@example.com");
    }

    private Policy createPolicy(Long id) {
        Policy policy = Policy.builder()
                .title("테스트")
                .category(Category.JOBS)
                .applyEnd(LocalDate.of(2026, 6, 30))
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
