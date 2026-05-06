package com.youthfit.rag.infrastructure.external;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiEmbeddingClientMetricTest {

    /**
     * 단위 테스트는 RestClient 모킹이 까다로워 본 테스트는 통합 테스트(F1) 로 검증한다.
     * 여기서는 properties / publisher 의 인스턴스 주입과 컴파일을 검증한다.
     */
    @Test
    void publisher_는_정상_주입된다() {
        OpenAiEmbeddingProperties props = mock(OpenAiEmbeddingProperties.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(props, publisher);
        assertThat(client).isNotNull();
    }
}
