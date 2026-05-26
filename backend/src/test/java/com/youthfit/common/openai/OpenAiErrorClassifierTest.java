package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiErrorClassifierTest {

    private final OpenAiErrorClassifier classifier = new OpenAiErrorClassifier();

    @Test
    @DisplayName("429 with Retry-After 정수 초 헤더 → RetryableOpenAiException(retryAfter=5s)")
    void classifies429WithRetryAfterSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "5");
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                headers, "body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).getRetryAfter())
                .contains(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("429 without Retry-After 헤더 → RetryableOpenAiException(retryAfter=null)")
    void classifies429WithoutRetryAfter() {
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).getRetryAfter()).isEmpty();
    }

    @Test
    @DisplayName("503 Service Unavailable → RetryableOpenAiException(retryAfter=null)")
    void classifies5xx() {
        HttpServerErrorException raw = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
        assertThat(((RetryableOpenAiException) classified).getRetryAfter()).isEmpty();
    }

    @Test
    @DisplayName("401 Unauthorized → 원본 예외 그대로 re-throw")
    void rethrows4xxNon429() {
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isSameAs(raw);
    }

    @Test
    @DisplayName("ResourceAccessException (network timeout) → RetryableOpenAiException")
    void classifiesResourceAccessException() {
        ResourceAccessException raw = new ResourceAccessException(
                "I/O error", new SocketTimeoutException("read timeout"));

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
    }

    @Test
    @DisplayName("raw IOException → RetryableOpenAiException")
    void classifiesRawIOException() {
        IOException raw = new IOException("broken pipe");

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isInstanceOf(RetryableOpenAiException.class);
    }

    @Test
    @DisplayName("NullPointerException 같은 비-HTTP 예외 → 원본 그대로 (RuntimeException 으로 래핑)")
    void rethrowsUnknownRuntimeException() {
        NullPointerException raw = new NullPointerException("npe");

        RuntimeException classified = classifier.classify(raw);

        assertThat(classified).isSameAs(raw);
    }
}
