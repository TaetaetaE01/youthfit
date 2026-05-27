package com.youthfit.qna.infrastructure.external;

import com.youthfit.common.exception.RetryableOpenAiException;
import com.youthfit.common.openai.OpenAiErrorClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OpenAiQnaClient 의 retry 경로 검증:
 * RestClientException 발생 시 errorClassifier.classify() 가 호출되고 결과가 throw 됨을 확인한다.
 *
 * RestClient 의 fluent chain 은 intermediate mock 을 직접 연결해 모킹한다.
 */
class OpenAiQnaClientRetryTest {

    private OpenAiQnaProperties mockProperties() {
        OpenAiQnaProperties properties = mock(OpenAiQnaProperties.class);
        when(properties.getModel()).thenReturn("gpt-4o-mini");
        when(properties.getApiKey()).thenReturn("test-key");
        when(properties.getMaxTokens()).thenReturn(1024);
        return properties;
    }

    /**
     * RestClient fluent chain 을 단계별 mock 으로 구성해 지정된 예외를 throw 하도록 설정한다.
     * body(Class) 최종 호출에서 exception 을 throw 한다.
     */
    @SuppressWarnings("unchecked")
    private RestClient.Builder chainedBuilder(RuntimeException throwOnBodyCall) {
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(responseSpec.body(any(Class.class))).thenThrow(throwOnBodyCall);

        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);

        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenReturn(uriSpec);

        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);
        return builder;
    }

    /**
     * generateAnswer — HTTP 4xx(429) 발생 시 classifier 를 거쳐 RetryableOpenAiException 이 throw 된다.
     */
    @Test
    void generateAnswer_4xx_시_classifier_가_호출되고_RetryableOpenAiException_throw() {
        HttpClientErrorException cause = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        OpenAiErrorClassifier classifier = mock(OpenAiErrorClassifier.class);
        when(classifier.classify(any())).thenReturn(
                new RetryableOpenAiException("429 Too Many Requests", cause, null));

        OpenAiQnaClient client = new OpenAiQnaClient(
                mockProperties(), mock(ApplicationEventPublisher.class), classifier, chainedBuilder(cause));

        assertThatThrownBy(() -> client.generateAnswer("정책명", null, "ctx", "질문", chunk -> {}))
                .isInstanceOf(RetryableOpenAiException.class);

        verify(classifier).classify(any(HttpClientErrorException.class));
    }

    /**
     * generateFollowUpQuestions — HTTP 5xx 발생 시 classifier 를 거쳐 RetryableOpenAiException 이 throw 된다.
     */
    @Test
    void generateFollowUpQuestions_5xx_시_classifier_가_호출되고_RetryableOpenAiException_throw() {
        HttpServerErrorException cause = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        OpenAiErrorClassifier classifier = mock(OpenAiErrorClassifier.class);
        when(classifier.classify(any())).thenReturn(
                new RetryableOpenAiException("5xx Internal Server Error", cause, null));

        OpenAiQnaClient client = new OpenAiQnaClient(
                mockProperties(), mock(ApplicationEventPublisher.class), classifier, chainedBuilder(cause));

        assertThatThrownBy(() -> client.generateFollowUpQuestions("정책명", "질문", "답변", "ctx"))
                .isInstanceOf(RetryableOpenAiException.class);

        verify(classifier).classify(any(HttpServerErrorException.class));
    }
}
