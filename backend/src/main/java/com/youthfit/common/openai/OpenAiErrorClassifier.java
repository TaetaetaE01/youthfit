package com.youthfit.common.openai;

import com.youthfit.common.exception.RetryableOpenAiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * OpenAI raw 예외를 retry 가능한 transient 실패와 영구 실패로 분류한다.
 *
 * Why: 4xx (non-429) 영구 실패는 retry 해도 동일 응답을 받는다.
 * 비용/시간 낭비를 피하려면 transient 만 RetryableOpenAiException 으로 래핑한다.
 */
@Component
public class OpenAiErrorClassifier {

    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    public RuntimeException classify(Throwable raw) {
        if (raw instanceof HttpClientErrorException httpClient) {
            if (httpClient.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                Duration retryAfter = parseRetryAfter(httpClient).orElse(null);
                return new RetryableOpenAiException(
                        "OpenAI 429 Too Many Requests", httpClient, retryAfter);
            }
            return httpClient;
        }
        if (raw instanceof HttpServerErrorException httpServer) {
            return new RetryableOpenAiException(
                    "OpenAI 5xx " + httpServer.getStatusCode(), httpServer, null);
        }
        if (raw instanceof ResourceAccessException resourceAccess) {
            return new RetryableOpenAiException(
                    "OpenAI 네트워크 I/O 실패", resourceAccess, null);
        }
        if (raw instanceof IOException) {
            return new RetryableOpenAiException(
                    "OpenAI I/O 실패", raw, null);
        }
        if (raw instanceof RuntimeException rt) {
            return rt;
        }
        return new RuntimeException("알 수 없는 OpenAI 호출 실패", raw);
    }

    private Optional<Duration> parseRetryAfter(HttpClientErrorException e) {
        String value = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds < 0) return Optional.empty();
            return Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException ignored) {
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(value.trim(), HTTP_DATE);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            Duration diff = Duration.between(now, when);
            if (diff.isNegative()) return Optional.empty();
            return Optional.of(diff);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
