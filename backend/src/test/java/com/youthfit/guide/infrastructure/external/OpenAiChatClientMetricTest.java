package com.youthfit.guide.infrastructure.external;

import com.youthfit.common.openai.OpenAiErrorClassifier;
import com.youthfit.common.util.TokenCounter;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiChatClientMetricTest {

    private static RestClient.Builder mockBuilder() {
        RestClient.Builder b = mock(RestClient.Builder.class);
        when(b.build()).thenReturn(mock(RestClient.class));
        return b;
    }

    @Test
    void emitMetric_은_usage_를_파싱해_GUIDE_모듈_이벤트를_발행한다() throws Exception {
        OpenAiChatProperties props = mock(OpenAiChatProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        OpenAiChatClient client = new OpenAiChatClient(props, publisher, mock(TokenCounter.class), mock(OpenAiErrorClassifier.class), mockBuilder());

        JsonNode response = new ObjectMapper().readTree("""
                {"choices":[{"message":{"content":"{}"}}],
                 "usage":{"prompt_tokens": 1234, "completion_tokens": 567}}
                """);

        Method m = OpenAiChatClient.class.getDeclaredMethod("emitMetric", JsonNode.class);
        m.setAccessible(true);
        m.invoke(client, response);

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        LlmCallRecorded event = captor.getValue();
        assertThat(event.module()).isEqualTo(LlmModule.GUIDE);
        assertThat(event.model()).isEqualTo("gpt-4o-mini");
        assertThat(event.promptTokens()).isEqualTo(1234);
        assertThat(event.completionTokens()).isEqualTo(567);
    }

    @Test
    void usage_누락_시_0_으로_적재_시도() throws Exception {
        OpenAiChatProperties props = mock(OpenAiChatProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        OpenAiChatClient client = new OpenAiChatClient(props, publisher, mock(TokenCounter.class), mock(OpenAiErrorClassifier.class), mockBuilder());

        JsonNode response = new ObjectMapper().readTree("""
                {"choices":[{"message":{"content":"{}"}}]}
                """);

        Method m = OpenAiChatClient.class.getDeclaredMethod("emitMetric", JsonNode.class);
        m.setAccessible(true);
        m.invoke(client, response);

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().promptTokens()).isZero();
        assertThat(captor.getValue().completionTokens()).isZero();
    }
}
