package com.youthfit.qna.infrastructure.external;

import com.youthfit.common.openai.OpenAiErrorClassifier;
import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiQnaClientMetricTest {

    @Test
    void readStreamResponse_는_usage_청크를_파싱해_QNA_이벤트를_발행한다() throws Exception {
        OpenAiQnaProperties props = mock(OpenAiQnaProperties.class);
        when(props.getModel()).thenReturn("gpt-4o-mini");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        RestClient.Builder builderMock = mock(RestClient.Builder.class);
        when(builderMock.build()).thenReturn(mock(RestClient.class));

        OpenAiQnaClient client = new OpenAiQnaClient(props, publisher, mock(OpenAiErrorClassifier.class), builderMock);

        // SSE 형식 더미 — content chunk 2 + usage chunk 1 + [DONE]
        String sse = """
                data: {"choices":[{"delta":{"content":"안녕"}}]}

                data: {"choices":[{"delta":{"content":"하세요"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}}

                data: [DONE]

                """;
        var inputStream = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));

        StringBuilder collected = new StringBuilder();
        Consumer<String> consumer = collected::append;

        Method m = OpenAiQnaClient.class.getDeclaredMethod("readStreamResponse",
                java.io.InputStream.class, Consumer.class);
        m.setAccessible(true);
        Object result = m.invoke(client, inputStream, consumer);

        assertThat(result).isEqualTo("안녕하세요");
        assertThat(collected.toString()).isEqualTo("안녕하세요");

        ArgumentCaptor<LlmCallRecorded> captor = ArgumentCaptor.forClass(LlmCallRecorded.class);
        verify(publisher).publishEvent(captor.capture());
        LlmCallRecorded event = captor.getValue();
        assertThat(event.module()).isEqualTo(LlmModule.QNA);
        assertThat(event.promptTokens()).isEqualTo(12);
        assertThat(event.completionTokens()).isEqualTo(34);
    }
}
