package com.youthfit.user.infrastructure.email;

import com.youthfit.auth.infrastructure.jwt.JwtAuthenticationFilter;
import com.youthfit.common.config.SecurityConfig;
import com.youthfit.ingestion.infrastructure.config.InternalApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SesEventListener.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class}))
class SesEventListenerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SnsMessageVerifier verifier;

    @MockitoBean
    SesEventHandler handler;

    @MockitoBean
    SesEventPayloadParser parser;

    @Test
    @WithMockUser
    void notification_event_OK_handler_호출() throws Exception {
        String snsBody = """
            {"Type":"Notification","TopicArn":"arn","MessageId":"sns-1",
             "Signature":"sig","SigningCertURL":"https://sns.us-east-1.amazonaws.com/x.pem",
             "SignatureVersion":"1","Timestamp":"2026-05-05T10:00:00Z",
             "Message":"{\\"eventType\\":\\"Delivery\\",\\"mail\\":{\\"messageId\\":\\"m-1\\"}}"}
            """;
        when(parser.parse(any())).thenReturn(SesEvent.delivery("m-1"));

        mvc.perform(post("/api/internal/notifications/ses-event")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(snsBody))
            .andExpect(status().isOk());

        verify(verifier).verify(any());
        verify(handler).handle(any());
    }

    @Test
    @WithMockUser
    void subscription_confirmation_subscribeUrl_호출() throws Exception {
        String snsBody = """
            {"Type":"SubscriptionConfirmation","TopicArn":"arn","MessageId":"sns-2",
             "SubscribeURL":"https://sns.example.com/?Token=xx",
             "Signature":"s","SigningCertURL":"https://sns.us-east-1.amazonaws.com/x.pem",
             "SignatureVersion":"1","Timestamp":"t","Message":""}
            """;
        mvc.perform(post("/api/internal/notifications/ses-event")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(snsBody))
            .andExpect(status().isOk());

        verify(verifier).verify(any());
        verify(verifier).confirmSubscription("https://sns.example.com/?Token=xx");
        verify(handler, never()).handle(any());
    }
}
